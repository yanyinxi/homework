package com.homework.asset.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * API Key 认证过滤器。
 *
 * 功能：
 * - 通过 Header X-API-Key 进行认证
 * - 公开路径（Actuator、Swagger）无需认证
 * - 支持 ROLE_USER 和 ROLE_ADMIN 角色
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter implements Ordered {

  /** API Key Header 名称 */
  private static final String API_KEY_HEADER = "X-API-Key";

  /** 无需认证的公开路径 */
  private static final String[] PUBLIC_PATHS = {
      "/actuator/health", "/actuator/info", "/actuator/prometheus", "/actuator/metrics",
      "/swagger-ui", "/api-docs", "/swagger-ui.html", "/v3/api-docs", "/webjars"
  };

  private static final String UNAUTHORIZED_JSON = "{\"code\":401,\"message\":\"%s\"}";

  private final ApiKeyProperties properties;

  /**
   * 预计算的 API Key 字节数组缓存，避免每次请求重复编码。
   */
  private volatile byte[][] keyBytesCache;

  /**
   * 构造函数，注入 API Key 配置属性。
   *
   * @param properties API Key 配置属性，包含启用的 key 列表和开关
   */
  public ApiKeyAuthFilter(ApiKeyProperties properties) {
    this.properties = properties;
  }

  /**
   * 实现 Ordered 接口，使 Spring Security 能获取 Filter 的执行顺序。
   * 注：Spring Security 6.x 要求自定义 Filter 必须实现 Ordered 接口，
   * 才能在 addFilterBefore() 中正确识别顺序。
   *
   * @return 优先级数值，值越小越先执行。HIGHEST_PRECEDENCE + 100 表示在最高优先级之后第100位
   */
  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 100;
  }

  /**
   * 初始化 Key 字节数组缓存。
   */
  private void initKeyCache() {
    if (keyBytesCache == null) {
      List<ApiKeyProperties.ApiKeyEntry> apiKeys = properties.getApiKeys();
      keyBytesCache = apiKeys.stream()
          .map(e -> e.getKey().getBytes(StandardCharsets.UTF_8))
          .toArray(byte[][]::new);
    }
  }

  /**
   * 判断当前请求是否应跳过过滤器。
   * 公开路径（Actuator 健康检查、Swagger 文档等）无需认证，直接放行。
   *
   * @param request HTTP 请求对象
   * @return true 表示跳过过滤器，false 表示需要执行过滤
   */
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    for (String publicPath : PUBLIC_PATHS) {
      if (path.equals(publicPath) || path.startsWith(publicPath + "/")) {
        return true;
      }
    }
    return false;
  }

  /**
   * 核心 API Key 认证过滤逻辑。
   * 从请求 Header 中提取 X-API-Key，验证其有效性并设置安全上下文。
   * 若认证失败或 API Key 无效，返回 401 未授权响应。
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

    if (!properties.isEnabled()) {
      filterChain.doFilter(request, response);
      return;
    }

    initKeyCache();

    String apiKey = request.getHeader(API_KEY_HEADER);

    if (apiKey == null || apiKey.isBlank()) {
      sendUnauthorized(response, "Missing API Key");
      return;
    }

    ApiKeyProperties.ApiKeyEntry entry = findApiKey(apiKey);
    if (entry == null) {
      sendUnauthorized(response, "Invalid API Key");
      return;
    }

    Authentication auth = createAuthentication(entry);
    SecurityContextHolder.getContext().setAuthentication(auth);

    filterChain.doFilter(request, response);
  }

  /**
   * 在配置的 API Key 列表中查找匹配的条目。
   * 使用预计算的缓存字节数组进行比对，避免重复编码。
   *
   * @param key 待验证的 API Key 字符串
   * @return 匹配的 ApiKeyEntry，未找到则返回 null
   */
  private ApiKeyProperties.ApiKeyEntry findApiKey(String key) {
    byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
    List<ApiKeyProperties.ApiKeyEntry> apiKeys = properties.getApiKeys();

    for (int i = 0; i < keyBytesCache.length; i++) {
      if (MessageDigest.isEqual(keyBytes, keyBytesCache[i])) {
        return apiKeys.get(i);
      }
    }
    return null;
  }

  /**
   * 根据 API Key 条目创建 Spring Security 认证对象。
   * 解析角色字符串并构建权限列表，用于后续的权限校验。
   *
   * @param entry API Key 配置条目，包含用户名和角色信息
   * @return 认证对象，包含用户名和权限列表
   */
  private Authentication createAuthentication(ApiKeyProperties.ApiKeyEntry entry) {
    List<SimpleGrantedAuthority> authorities = Arrays.stream(entry.getRoles().split(","))
        .map(String::trim)
        .map(SimpleGrantedAuthority::new)
        .toList();

    return new UsernamePasswordAuthenticationToken(entry.getName(), null, authorities);
  }

  /**
   * 发送 401 未授权响应。
   * 返回 JSON 格式的错误信息，供前端解析和展示。
   *
   * @param response HTTP 响应对象
   * @param message 错误消息
   * @throws IOException IO 异常
   */
  private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json;charset=UTF-8");
    String escapedMessage = message.replace("\\", "\\\\").replace("\"", "\\\"");
    response.getWriter().write(String.format(UNAUTHORIZED_JSON, escapedMessage));
  }
}
