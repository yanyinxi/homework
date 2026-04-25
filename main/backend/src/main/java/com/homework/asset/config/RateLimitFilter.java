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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

  private static final String API_KEY_HEADER = "X-API-Key";
  private static final String RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";
  private static final long BUCKET_EXPIRY_MINUTES = 30;

  private final Map<String, BucketWithTimestamp> buckets = new ConcurrentHashMap<>();
  private final int requestsPerSecond;
  private ScheduledExecutorService cleanupExecutor;

  public RateLimitFilter(@Value("${app.rate-limit.requests-per-second:10}") int requestsPerSecond) {
    this.requestsPerSecond = requestsPerSecond;
  }

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

  @PreDestroy
  public void destroy() {
    if (cleanupExecutor != null) {
      cleanupExecutor.shutdown();
    }
  }

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

  private String resolveKey(HttpServletRequest request) {
    String apiKey = request.getHeader(API_KEY_HEADER);
    if (apiKey != null && !apiKey.isBlank()) {
      return "apikey:" + apiKey;
    }
    return "ip:" + getClientIp(request);
  }

  private String getClientIp(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
      return xForwardedFor.split(",")[0].trim();
    }
    String xRealIp = request.getHeader("X-Real-IP");
    if (xRealIp != null && !xRealIp.isEmpty()) {
      return xRealIp;
    }
    return request.getRemoteAddr();
  }

  private Bucket createBucket() {
    Bandwidth limit =
        Bandwidth.classic(
            requestsPerSecond, Refill.intervally(requestsPerSecond, Duration.ofSeconds(1)));
    return Bucket.builder().addLimit(limit).build();
  }

  private void sendTooManyRequests(HttpServletResponse response) throws IOException {
    response.setStatus(429);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("{\"code\":429,\"message\":\"Rate limit exceeded. Try again later.\"}");
  }

  private static final class BucketWithTimestamp {
    private final Bucket bucket;
    private volatile long lastAccessTime;

    BucketWithTimestamp(Bucket bucket) {
      this.bucket = bucket;
      this.lastAccessTime = System.currentTimeMillis();
    }

    Bucket getBucket() {
      return bucket;
    }

    long getLastAccessTime() {
      return lastAccessTime;
    }

    void touch() {
      this.lastAccessTime = System.currentTimeMillis();
    }
  }
}
