package com.homework.asset.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("API Key 认证过滤器测试")
class ApiKeyAuthFilterTest {

  private ApiKeyAuthFilter filter;
  private ApiKeyProperties properties;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private FilterChain filterChain;

  @BeforeEach
  void setUp() {
    properties = new ApiKeyProperties();
    properties.setEnabled(true);

    ApiKeyProperties.ApiKeyEntry devKey = new ApiKeyProperties.ApiKeyEntry();
    devKey.setKey("test-api-key-001");
    devKey.setName("Test User");
    devKey.setRoles("ROLE_USER");

    ApiKeyProperties.ApiKeyEntry adminKey = new ApiKeyProperties.ApiKeyEntry();
    adminKey.setKey("test-api-key-002");
    adminKey.setName("Admin User");
    adminKey.setRoles("ROLE_ADMIN,ROLE_USER");

    properties.setApiKeys(List.of(devKey, adminKey));

    filter = new ApiKeyAuthFilter(properties);
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    filterChain = mock(FilterChain.class);

    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("有效 API Key 应通过认证")
  void shouldAuthenticateWithValidApiKey() throws ServletException, IOException {
    when(request.getHeader("X-API-Key")).thenReturn("test-api-key-001");

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("Test User");
  }

  @Test
  @DisplayName("无效 API Key 应返回 401")
  void shouldReturn401WithInvalidApiKey() throws ServletException, IOException {
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    when(request.getHeader("X-API-Key")).thenReturn("invalid-key");
    when(response.getWriter()).thenReturn(printWriter);

    filter.doFilterInternal(request, response, filterChain);

    verify(response).setStatus(401);
    verify(filterChain, never()).doFilter(request, response);
    assertThat(stringWriter.toString()).contains("Invalid API Key");
  }

  @Test
  @DisplayName("缺少 API Key 应返回 401")
  void shouldReturn401WithMissingApiKey() throws ServletException, IOException {
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    when(request.getHeader("X-API-Key")).thenReturn(null);
    when(response.getWriter()).thenReturn(printWriter);

    filter.doFilterInternal(request, response, filterChain);

    verify(response).setStatus(401);
    verify(filterChain, never()).doFilter(request, response);
    assertThat(stringWriter.toString()).contains("Missing API Key");
  }

  @Test
  @DisplayName("禁用认证时应直接放行")
  void shouldPassThroughWhenDisabled() throws ServletException, IOException {
    properties.setEnabled(false);
    when(request.getHeader("X-API-Key")).thenReturn(null);

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
  }

  @Test
  @DisplayName("Admin Key 应具有 ADMIN 角色")
  void shouldAssignAdminRole() throws ServletException, IOException {
    when(request.getHeader("X-API-Key")).thenReturn("test-api-key-002");

    filter.doFilterInternal(request, response, filterChain);

    var authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
    assertThat(authorities).extracting("authority").contains("ROLE_ADMIN", "ROLE_USER");
  }
}
