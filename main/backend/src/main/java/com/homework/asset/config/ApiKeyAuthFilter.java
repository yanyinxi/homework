package com.homework.asset.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** API Key 认证过滤器。通过 Header X-API-Key 进行认证。 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

  private static final String API_KEY_HEADER = "X-API-Key";
  private static final String[] PUBLIC_PATHS = {
    "/actuator/health", "/actuator/info", "/actuator/prometheus", "/actuator/metrics",
    "/swagger-ui", "/api-docs", "/swagger-ui.html"
  };
  private final ApiKeyProperties properties;

  public ApiKeyAuthFilter(ApiKeyProperties properties) {
    this.properties = properties;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    for (String publicPath : PUBLIC_PATHS) {
      if (path.startsWith(publicPath)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    if (!properties.isEnabled()) {
      filterChain.doFilter(request, response);
      return;
    }

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

  private ApiKeyProperties.ApiKeyEntry findApiKey(String key) {
    return properties.getApiKeys().stream()
        .filter(e -> e.getKey().equals(key))
        .findFirst()
        .orElse(null);
  }

  private Authentication createAuthentication(ApiKeyProperties.ApiKeyEntry entry) {
    List<SimpleGrantedAuthority> authorities =
        Arrays.stream(entry.getRoles().split(","))
            .map(String::trim)
            .map(SimpleGrantedAuthority::new)
            .toList();

    return new UsernamePasswordAuthenticationToken(entry.getName(), null, authorities);
  }

  private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\"}");
  }
}
