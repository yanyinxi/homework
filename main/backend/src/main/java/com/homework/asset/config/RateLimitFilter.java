package com.homework.asset.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 限流过滤器。
 *
 * 算法：Bucket4j 令牌桶
 *
 * 功能：
 * - API Key 级别限流（识别不同 API Key 消费者）
 * - IP 级别限流（未认证请求按 IP 限流）
 * - 自动清理过期 Bucket（防止内存泄漏）
 * - 返回 X-RateLimit-Remaining header
 *
 * 配置：app.rate-limit.requests-per-second（默认 10）
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter implements Ordered {

  private static final String API_KEY_HEADER = "X-API-Key";
  private static final String RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";
  private static final long BUCKET_EXPIRY_MINUTES = 30;

  private final Map<String, BucketWithTimestamp> buckets = new ConcurrentHashMap<>();
  private final int requestsPerSecond;
  private ScheduledExecutorService cleanupExecutor;

  /**
   * 构造函数，注入每秒请求数限制配置。
   *
   * @param requestsPerSecond 每秒允许的最大请求数，默认 10
   */
  public RateLimitFilter(@Value("${app.rate-limit.requests-per-second:10}") int requestsPerSecond) {
    this.requestsPerSecond = requestsPerSecond;
  }

  /**
   * 实现 Ordered 接口，使 Spring Security 能获取 Filter 的执行顺序。
   * 注：Spring Security 6.x 要求自定义 Filter 必须实现 Ordered 接口，
   * 才能在 addFilterBefore() 中正确识别顺序。
   *
   * @return 优先级数值，值越小越先执行。HIGHEST_PRECEDENCE + 50 表示在最高优先级之后第50位
   */
  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 50;
  }

  /**
   * 初始化定时清理任务。
   * 每 5 分钟清理过期的 Bucket，防止内存泄漏。
   */
  @PostConstruct
  public void init() {
    cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "rate-limit-cleanup");
      t.setDaemon(true);
      return t;
    });
    cleanupExecutor.scheduleAtFixedRate(
        this::cleanupExpiredBuckets, 5, 5, TimeUnit.MINUTES);
  }

  /**
   * 销毁定时任务线程池。
   */
  @PreDestroy
  public void destroy() {
    if (cleanupExecutor != null) {
      cleanupExecutor.shutdown();
    }
  }

  /**
   * 核心限流过滤逻辑。
   * 根据 API Key 或 IP 获取对应的令牌桶，尝试消费一个令牌。
   * 成功则放行请求，失败则返回 429 状态码。
   *
   * @param request HTTP 请求对象
   * @param response HTTP 响应对象
   * @param filterChain 过滤器链
   * @throws ServletException Servlet 异常
   * @throws IOException IO 异常
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String key = resolveKey(request);
    BucketWithTimestamp wrapper = buckets.computeIfAbsent(key, k -> new BucketWithTimestamp(createBucket()));
    wrapper.touch();

    Bucket bucket = wrapper.getBucket();
    if (bucket.tryConsume(1)) {
      response.setHeader(RATE_LIMIT_REMAINING, String.valueOf(bucket.getAvailableTokens()));
      filterChain.doFilter(request, response);
    } else {
      sendTooManyRequests(response);
    }
  }

  /**
   * 清理过期的令牌桶。
   * 移除超过 30 分钟未访问的 Bucket，释放内存。
   */
  private void cleanupExpiredBuckets() {
    long now = System.currentTimeMillis();
    long expiryMillis = TimeUnit.MINUTES.toMillis(BUCKET_EXPIRY_MINUTES);

    Iterator<Map.Entry<String, BucketWithTimestamp>> it = buckets.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, BucketWithTimestamp> entry = it.next();
      if (now - entry.getValue().getLastAccessTime() > expiryMillis) {
        it.remove();
      }
    }
  }

  /**
   * 解析限流 Key。
   * 优先使用 API Key，未认证请求使用客户端 IP。
   *
   * @param request HTTP 请求对象
   * @return 限流 Key，格式为 "apikey:xxx" 或 "ip:xxx"
   */
  private String resolveKey(HttpServletRequest request) {
    String apiKey = request.getHeader(API_KEY_HEADER);
    if (apiKey != null && !apiKey.isBlank()) {
      return "apikey:" + apiKey;
    }
    return "ip:" + getClientIp(request);
  }

  /**
   * 获取客户端真实 IP。
   * 依次检查 X-Forwarded-For、X-Real-IP、RemoteAddr。
   * X-Forwarded-For 伪造检测：若第一段 IP 属于私有/回环地址段，则降级到后续 Header。
   *
   * @param request HTTP 请求对象
   * @return 客户端 IP 地址
   */
  private String getClientIp(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isEmpty()) {
      String first = xff.split(",")[0].trim();
      if (isValidPublicIp(first)) {
        return first;
      }
    }
    String xRealIp = request.getHeader("X-Real-IP");
    if (xRealIp != null && !xRealIp.isEmpty()) {
      return xRealIp;
    }
    return request.getRemoteAddr();
  }

  /**
   * 验证 IP 是否为有效公网 IP。
   * 排除私有地址段（10.0.0.0/8、172.16.0.0/12、192.168.0.0/16）和回环地址（127.0.0.0/8）。
   *
   * @param ip 待验证的 IP 地址字符串
   * @return true 表示公网 IP，false 表示私有/回环 IP（应降级）
   */
  private boolean isValidPublicIp(String ip) {
    if (ip == null || ip.isEmpty()) {
      return false;
    }
    try {
      long ipLong = ipToLong(ip);
      // 127.0.0.0/8 — loopback
      if (inRange(ipLong, ipToLong("127.0.0.0"), ipToLong("127.255.255.255"))) {
        return false;
      }
      // 10.0.0.0/8
      if (inRange(ipLong, ipToLong("10.0.0.0"), ipToLong("10.255.255.255"))) {
        return false;
      }
      // 172.16.0.0/12
      if (inRange(ipLong, ipToLong("172.16.0.0"), ipToLong("172.31.255.255"))) {
        return false;
      }
      // 192.168.0.0/16
      if (inRange(ipLong, ipToLong("192.168.0.0"), ipToLong("192.168.255.255"))) {
        return false;
      }
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private long ipToLong(String ip) {
    String[] parts = ip.split("\\.");
    long result = 0;
    for (int i = 0; i < 4; i++) {
      result = (result << 8) | Integer.parseInt(parts[i]);
    }
    return result;
  }

  private boolean inRange(long ip, long start, long end) {
    return ip >= start && ip <= end;
  }

  /**
   * 创建令牌桶。
   * 使用经典令牌桶算法，每秒补充指定数量的令牌。
   *
   * @return Bucket 实例
   */
  private Bucket createBucket() {
    Bandwidth limit =
        Bandwidth.classic(
            requestsPerSecond, Refill.intervally(requestsPerSecond, Duration.ofSeconds(1)));
    return Bucket.builder().addLimit(limit).build();
  }

  /**
   * 发送 429 Too Many Requests 响应。
   *
   * @param response HTTP 响应对象
   * @throws IOException IO 异常
   */
  private void sendTooManyRequests(HttpServletResponse response) throws IOException {
    response.setStatus(429);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("{\"code\":429,\"message\":\"Rate limit exceeded. Try again later.\"}");
  }

  /** 令牌桶包装类，包含 Bucket 实例和最后访问时间。 */
  private static final class BucketWithTimestamp {
    private final Bucket bucket;
    private volatile long lastAccessTime;

    /**
     * 构造函数。
     *
     * @param bucket 令牌桶实例
     */
    BucketWithTimestamp(Bucket bucket) {
      this.bucket = bucket;
      this.lastAccessTime = System.currentTimeMillis();
    }

    /**
     * 获取令牌桶实例。
     *
     * @return Bucket 实例
     */
    Bucket getBucket() {
      return bucket;
    }

    /**
     * 获取最后访问时间。
     *
     * @return 最后访问时间戳（毫秒）
     */
    long getLastAccessTime() {
      return lastAccessTime;
    }

    /** 更新最后访问时间为当前时间。 */
    void touch() {
      this.lastAccessTime = System.currentTimeMillis();
    }
  }
}
