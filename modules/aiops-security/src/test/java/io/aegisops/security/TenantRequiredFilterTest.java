package io.aegisops.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class TenantRequiredFilterTest {

  @Test
  void shouldSkipZabbixIntegrationWebhookPath() {
    TestableTenantRequiredFilter filter = newFilter();

    assertThat(filter.shouldSkip("/api/integrations/zabbix/events")).isTrue();
    assertThat(filter.shouldSkip("/api/integrations/zabbix/health")).isTrue();
  }

  @Test
  void shouldNotSkipNormalApiPathWhenTenantRequired() {
    TestableTenantRequiredFilter filter = newFilter();

    assertThat(filter.shouldSkip("/api/alerts")).isFalse();
    assertThat(filter.shouldSkip("/api/incidents")).isFalse();
  }

  @Test
  void shouldSkipHealthAndAuthAndActuatorPaths() {
    TestableTenantRequiredFilter filter = newFilter();

    assertThat(filter.shouldSkip("/health")).isTrue();
    assertThat(filter.shouldSkip("/actuator/health")).isTrue();
    assertThat(filter.shouldSkip("/api/auth/login")).isTrue();
    assertThat(filter.shouldSkip("/swagger-ui/index.html")).isTrue();
    assertThat(filter.shouldSkip("/v3/api-docs")).isTrue();
  }

  @Test
  void shouldSkipInternalAgentPathsOwnedByInternalAgentAuthentication() {
    TestableTenantRequiredFilter filter = newFilter();

    assertThat(filter.shouldSkip("/internal/agent/auth/probe")).isTrue();
  }

  @Test
  void shouldNotFilterWhenTenantRequiredDisabled() {
    AiopsSecurityProperties properties = new AiopsSecurityProperties();
    properties.setTenantRequired(false);

    TestableTenantRequiredFilter filter =
        new TestableTenantRequiredFilter(
            properties,
            mock(SecurityErrorResponseWriter.class),
            mock(TenantSecurityAuditService.class));

    assertThat(filter.shouldSkip("/api/alerts")).isTrue();
  }

  private TestableTenantRequiredFilter newFilter() {
    AiopsSecurityProperties properties = new AiopsSecurityProperties();
    properties.setTenantRequired(true);

    return new TestableTenantRequiredFilter(
        properties,
        mock(SecurityErrorResponseWriter.class),
        mock(TenantSecurityAuditService.class));
  }

  private static class TestableTenantRequiredFilter extends TenantRequiredFilter {
    TestableTenantRequiredFilter(
        AiopsSecurityProperties properties,
        SecurityErrorResponseWriter responseWriter,
        TenantSecurityAuditService auditService) {
      super(properties, responseWriter, auditService);
    }

    boolean shouldSkip(String path) {
      MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
      request.setRequestURI(path);
      return shouldNotFilter(request);
    }
  }
}
