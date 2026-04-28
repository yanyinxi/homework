package com.homework.asset.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
@EnableMethodSecurity
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
        /**
         * 注册自定义 Filter 到 Spring Security 过滤器链。
         *
         * 重要说明（Spring Security 6.x）：
         * - addFilterBefore(instance, targetClass) 的第二个参数必须是 Spring Security 内置的 Filter Class，
         *   不能是自定义 Filter（否则会报 "does not have a registered order" 错误）
         * - 自定义 Filter 的执行顺序通过实现 Ordered 接口的 getOrder() 方法控制
         * - 两个 Filter 都以 UsernamePasswordAuthenticationFilter.class 为参照点
         *
         * 执行顺序：
         * 1. RateLimitFilter (getOrder = HIGHEST_PRECEDENCE + 50 = 10050) → 先限流
         * 2. ApiKeyAuthFilter (getOrder = HIGHEST_PRECEDENCE + 100 = 10100) → 再认证
         * 3. Controller
         */
        .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  /**
   * 禁止 Spring Boot 将 ApiKeyAuthFilter 自动注册为通用 Servlet Filter。
   * 该 Filter 只能通过 Spring Security 过滤器链执行，否则 OncePerRequestFilter
   * 的防重复执行机制会导致它在 Security 链中跳过，造成 403。
   */
  @Bean
  public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilterRegistration(ApiKeyAuthFilter filter) {
    FilterRegistrationBean<ApiKeyAuthFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  /**
   * 禁止 Spring Boot 将 RateLimitFilter 自动注册为通用 Servlet Filter。
   * 原因同上：OncePerRequestFilter 防重复执行机制会导致 Security 链中跳过。
   */
  @Bean
  public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
    FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));
    configuration.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Content-Type", "X-API-Key", "Authorization"));
    configuration.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }
}
