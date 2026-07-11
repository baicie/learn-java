package io.aegisops.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TenantRateLimitFilterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void loginWithoutTenantMustStillBeRateLimited() throws Exception {
    AiopsQuotaProperties properties = new AiopsQuotaProperties();
    properties.setAnonymousRequestsPerMinute(1);

    InMemoryTenantRateLimiter limiter = new InMemoryTenantRateLimiter();

    TenantRateLimitFilter filter =
        new TenantRateLimitFilter(
            properties, limiter, new SecurityErrorResponseWriter(objectMapper), noopAudit());

    MockHttpServletResponse first = new MockHttpServletResponse();
    filter.doFilter(loginRequest("203.0.113.1"), first, noopChain());
    assertThat(first.getStatus()).isEqualTo(200);

    MockHttpServletResponse second = new MockHttpServletResponse();
    filter.doFilter(loginRequest("203.0.113.1"), second, noopChain());

    assertThat(second.getStatus()).isEqualTo(429);
    JsonNode body = objectMapper.readTree(second.getContentAsByteArray());
    assertThat(body.path("errorCode").asText()).isEqualTo("RATE_LIMITED");
    assertThat(second.getHeader("Retry-After")).isNotBlank();
  }

  @Test
  void differentClientAddressesMustHaveIndependentBuckets() throws Exception {
    AiopsQuotaProperties properties = new AiopsQuotaProperties();
    properties.setAnonymousRequestsPerMinute(1);

    TenantRateLimitFilter filter =
        new TenantRateLimitFilter(
            properties,
            new InMemoryTenantRateLimiter(),
            new SecurityErrorResponseWriter(objectMapper),
            noopAudit());

    MockHttpServletResponse first = new MockHttpServletResponse();
    filter.doFilter(loginRequest("203.0.113.1"), first, noopChain());
    assertThat(first.getStatus()).isEqualTo(200);

    MockHttpServletResponse second = new MockHttpServletResponse();
    filter.doFilter(loginRequest("203.0.113.2"), second, noopChain());
    assertThat(second.getStatus()).isEqualTo(200);
  }

  @Test
  void backendFailureMustReturnUniformJson() throws Exception {
    RateLimitService broken =
        (key, limit, window) -> {
          throw new AppException(
              ErrorCode.RATE_LIMIT_BACKEND_UNAVAILABLE, "redis unavailable");
        };

    TenantRateLimitFilter filter =
        new TenantRateLimitFilter(
            new AiopsQuotaProperties(),
            broken,
            new SecurityErrorResponseWriter(objectMapper),
            noopAudit());

    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(
        loginRequest("203.0.113.5"),
        response,
        (req, res) -> {
          throw new AssertionError("filter chain must not continue when backend fails");
        });

    assertThat(response.getStatus()).isEqualTo(503);
    JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
    assertThat(body.path("errorCode").asText())
        .isEqualTo("RATE_LIMIT_BACKEND_UNAVAILABLE");
    assertThat(body.path("success").asBoolean()).isFalse();
  }

  @Test
  void mustNotSkipNonApiPaths() {
    AiopsQuotaProperties properties = new AiopsQuotaProperties();
    properties.setRateLimitEnabled(true);

    TenantRateLimitFilter filter =
        new TenantRateLimitFilter(
            properties,
            new InMemoryTenantRateLimiter(),
            new SecurityErrorResponseWriter(objectMapper),
            noopAudit());

    MockHttpServletRequest consoleRequest = new MockHttpServletRequest();
    consoleRequest.setRequestURI("/console/index.html");
    assertThat(filter.shouldNotFilter(consoleRequest)).isTrue();

    MockHttpServletRequest apiRequest = new MockHttpServletRequest();
    apiRequest.setRequestURI("/api/work-record/records");
    assertThat(filter.shouldNotFilter(apiRequest)).isFalse();
  }

  @Test
  void disabledRateLimitMustBypassFilter() throws Exception {
    AiopsQuotaProperties properties = new AiopsQuotaProperties();
    properties.setRateLimitEnabled(false);

    TenantRateLimitFilter filter =
        new TenantRateLimitFilter(
            properties,
            new InMemoryTenantRateLimiter(),
            new SecurityErrorResponseWriter(objectMapper),
            noopAudit());

    MockHttpServletRequest request = loginRequest("203.0.113.10");
    assertThat(filter.shouldNotFilter(request)).isTrue();
  }

  @Test
  void anonymousPublicApiMustUseAnonymousBucket() throws Exception {
    AiopsQuotaProperties properties = new AiopsQuotaProperties();
    properties.setAnonymousRequestsPerMinute(1);

    TenantRateLimitFilter filter =
        new TenantRateLimitFilter(
            properties,
            new InMemoryTenantRateLimiter(),
            new SecurityErrorResponseWriter(objectMapper),
            noopAudit());

    MockHttpServletRequest first = new MockHttpServletRequest();
    first.setRequestURI("/api/health");
    first.setRemoteAddr("203.0.113.20");

    MockHttpServletResponse firstResponse = new MockHttpServletResponse();
    filter.doFilter(first, firstResponse, noopChain());
    assertThat(firstResponse.getStatus()).isEqualTo(200);

    MockHttpServletResponse secondResponse = new MockHttpServletResponse();
    filter.doFilter(first, secondResponse, noopChain());
    assertThat(secondResponse.getStatus()).isEqualTo(429);
    JsonNode body = objectMapper.readTree(secondResponse.getContentAsByteArray());
    assertThat(body.path("errorCode").asText()).isEqualTo("RATE_LIMITED");
  }

  @Test
  void shouldSetRateLimitResponseHeadersOnAllowedRequest() throws Exception {
    AiopsQuotaProperties properties = new AiopsQuotaProperties();
    properties.setAnonymousRequestsPerMinute(5);

    TenantRateLimitFilter filter =
        new TenantRateLimitFilter(
            properties,
            new InMemoryTenantRateLimiter(),
            new SecurityErrorResponseWriter(objectMapper),
            noopAudit());

    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(loginRequest("203.0.113.30"), response, noopChain());

    assertThat(response.getHeader("RateLimit-Limit")).isEqualTo("5");
    assertThat(response.getHeader("RateLimit-Remaining")).isEqualTo("4");
    assertThat(response.getHeader("RateLimit-Reset")).isNotBlank();
  }

  private MockHttpServletRequest loginRequest(String remoteAddr) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI("/api/auth/login");
    @SuppressWarnings("null")
    String address = remoteAddr;
    request.setRemoteAddr(address);
    return request;
  }

  private jakarta.servlet.FilterChain noopChain() {
    return (request, response) -> {};
  }

  private TenantSecurityAuditService noopAudit() {
    // TenantSecurityAuditService.record() 已吞掉 repository 抛出的异常，
    // 因此传入 null 仅会让 audit 写入被静默忽略，对单元测试不影响断言。
    return new TenantSecurityAuditService(null, objectMapper);
  }
}
