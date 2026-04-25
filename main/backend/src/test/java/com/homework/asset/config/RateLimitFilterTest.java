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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("限流过滤器测试")
class RateLimitFilterTest {

  private RateLimitFilter filter;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private FilterChain filterChain;

  @BeforeEach
  void setUp() {
    filter = new RateLimitFilter(5);
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    filterChain = mock(FilterChain.class);
  }

  @Test
  @DisplayName("未超限请求应正常通过")
  void shouldAllowRequestsWithinLimit() throws ServletException, IOException {
    when(request.getHeader("X-API-Key")).thenReturn("test-key");
    when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    when(request.getHeader("X-Real-IP")).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");

    for (int i = 0; i < 3; i++) {
      filter.doFilterInternal(request, response, filterChain);
    }

    verify(filterChain, times(3)).doFilter(request, response);
    verify(response, times(3)).setHeader(eq("X-RateLimit-Remaining"), anyString());
  }

  @Test
  @DisplayName("超限请求应返回 429")
  void shouldReturn429WhenRateLimited() throws ServletException, IOException {
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    when(request.getHeader("X-API-Key")).thenReturn("test-key-429");
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    when(response.getWriter()).thenReturn(printWriter);

    for (int i = 0; i < 10; i++) {
      filter.doFilterInternal(request, response, filterChain);
    }

    verify(response, atLeastOnce()).setStatus(429);
    assertThat(stringWriter.toString()).contains("Rate limit exceeded");
  }

  @Test
  @DisplayName("应按 API Key 区分限流桶")
  void shouldUseSeparateBucketsForDifferentKeys() throws ServletException, IOException {
    HttpServletRequest request1 = mock(HttpServletRequest.class);
    HttpServletRequest request2 = mock(HttpServletRequest.class);

    when(request1.getHeader("X-API-Key")).thenReturn("key-1");
    when(request1.getRemoteAddr()).thenReturn("127.0.0.1");

    when(request2.getHeader("X-API-Key")).thenReturn("key-2");
    when(request2.getRemoteAddr()).thenReturn("127.0.0.1");

    for (int i = 0; i < 5; i++) {
      filter.doFilterInternal(request1, response, filterChain);
      filter.doFilterInternal(request2, response, filterChain);
    }

    verify(filterChain, times(10)).doFilter(any(), eq(response));
  }

  @Test
  @DisplayName("无 API Key 时应按 IP 限流")
  void shouldRateLimitByIpWhenNoApiKey() throws ServletException, IOException {
    when(request.getHeader("X-API-Key")).thenReturn(null);
    when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn("192.168.1.100");

    for (int i = 0; i < 3; i++) {
      filter.doFilterInternal(request, response, filterChain);
    }

    verify(filterChain, times(3)).doFilter(request, response);
  }

  @Test
  @DisplayName("应正确解析 X-Forwarded-For 头")
  void shouldParseXForwardedForHeader() throws ServletException, IOException {
    when(request.getHeader("X-API-Key")).thenReturn(null);
    when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 192.168.1.1, 172.16.0.1");

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
  }

  @Test
  @DisplayName("响应头应包含剩余配额")
  void shouldIncludeRemainingQuotaInHeader() throws ServletException, IOException {
    when(request.getHeader("X-API-Key")).thenReturn("quota-test");
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");

    filter.doFilterInternal(request, response, filterChain);

    verify(response).setHeader("X-RateLimit-Remaining", "4");
  }
}
