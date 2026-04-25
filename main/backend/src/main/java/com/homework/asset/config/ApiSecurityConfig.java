package com.homework.asset.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 安全配置。
 * 
 * 配置要点：
 * - API 端点需要认证
 * - Actuator/Swagger 端点公开访问
 * - 无状态 Session（JWT 友好）
 */
@Configuration
@EnableWebSecurity
public class ApiSecurityConfig {

  private final ApiKeyAuthFilter apiKeyAuthFilter;
  private final RateLimitFilter rateLimitFilter;

  @Value("${app.cors.allowed-origins:http://localhost,http://127.0.0.1}")
  private String[] allowedOrigins;

  /**
   * 构造函数，注入认证过滤器和限流过滤器。
   *
   * @param apiKeyAuthFilter API Key 认证过滤器
   * @param rateLimitFilter 限流过滤器
   */
  public ApiSecurityConfig(ApiKeyAuthFilter apiKeyAuthFilter, RateLimitFilter rateLimitFilter) {
    this.apiKeyAuthFilter = apiKeyAuthFilter;
    this.rateLimitFilter = rateLimitFilter;
  }

  /**
   * 创建安全过滤器链。
   * 配置 CORS、CSRF、Session 策略，定义公开路径和认证路径，
   * 并将限流过滤器和认证过滤器加入过滤器链。
   *
   * @param http HttpSecurity 配置对象
   * @return SecurityFilterChain 实例
   * @throws Exception 配置异常
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth
                    .requestMatchers(
                        "/actuator/health", "/actuator/info", "/actuator/prometheus",
                        "/actuator/metrics", "/actuator/metrics/**")
                    .permitAll()
                    .requestMatchers(
                        "/swagger-ui/**", "/api-docs/**", "/swagger-ui.html",
                        "/v3/api-docs", "/v3/api-docs/**", "/webjars/**")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(rateLimitFilter, ApiKeyAuthFilter.class);

    return http.build();
  }

  /**
   * 创建 CORS 配置源。
   * 允许配置的源访问 API，支持 GET、POST、DELETE 方法。
   *
   * @return CorsConfigurationSource 实例
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));
    configuration.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }
}
