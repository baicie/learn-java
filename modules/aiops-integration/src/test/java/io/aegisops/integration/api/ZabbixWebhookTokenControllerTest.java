package io.aegisops.integration.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import io.aegisops.common.exception.AppException;
import io.aegisops.common.security.AuthenticatedActor;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.integration.application.ZabbixWebhookTokenApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;

class ZabbixWebhookTokenControllerTest {
  @AfterEach
  void clearTenant() {
    TenantContext.clear();
    SecurityContextHolder.clearContext();
    MDC.clear();
  }

  @Test
  void returnsDatasourceScopedTokenWithoutAllowingHttpCaching() throws Exception {
    ZabbixWebhookTokenApplicationService service = mock(ZabbixWebhookTokenApplicationService.class);
    when(service.issueToken(
            "tenant-a",
            "ds-a",
            "user-id",
            "req-token-controller",
            "203.0.113.20",
            "portal-test-agent"))
        .thenReturn("zwh_datasource-token");
    TenantContext.setTenantId("tenant-a");
    MDC.put("requestId", "req-token-controller");
    AuthenticatedActor user = new AuthenticationFixture("user-id", "tenant-a");
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));

    var mvc =
        standaloneSetup(new ZabbixWebhookTokenController(service))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

    mvc.perform(
            get("/api/datasources/ds-a/zabbix-webhook-token")
                .principal(() -> "operator-a")
                .header("User-Agent", "portal-test-agent")
                .with(
                    request -> {
                      request.setRemoteAddr("203.0.113.20");
                      return request;
                    }))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(jsonPath("$.data.token").value("zwh_datasource-token"));

    verify(service)
        .issueToken(
            "tenant-a",
            "ds-a",
            "user-id",
            "req-token-controller",
            "203.0.113.20",
            "portal-test-agent");
  }

  @Test
  void requiresTenantContext() {
    ZabbixWebhookTokenController controller =
        new ZabbixWebhookTokenController(mock(ZabbixWebhookTokenApplicationService.class));

    assertThatThrownBy(
            () ->
                controller.token(
                    "ds-a",
                    new AuthenticationFixture("user-id", "tenant-a"),
                    new MockHttpServletRequest()))
        .isInstanceOfSatisfying(
            AppException.class,
            exception -> assertThat(exception.errorCode()).isEqualTo("TENANT_REQUIRED"));
  }

  @Test
  void requiresDatasourceWritePermission() throws Exception {
    Method endpoint =
        ZabbixWebhookTokenController.class.getDeclaredMethod(
            "token", String.class, AuthenticatedActor.class, HttpServletRequest.class);

    assertThat(endpoint.getAnnotation(PreAuthorize.class).value())
        .isEqualTo("hasAuthority('datasource:write')");
  }

  private record AuthenticationFixture(String id, String tenantId) implements AuthenticatedActor {}
}
