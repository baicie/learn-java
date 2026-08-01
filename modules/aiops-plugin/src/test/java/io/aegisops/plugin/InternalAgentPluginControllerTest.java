package io.aegisops.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.security.DiagnosisGrantClaims;
import io.aegisops.plugin.dto.AgentToolAuthorizeRequest;
import io.aegisops.plugin.dto.AgentToolAuthorizeResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InternalAgentPluginControllerTest {
  @Test
  void authorizeUsesDiagnosisGrantTenant() {
    PluginService service = mock(PluginService.class);
    when(service.authorizeTool(
            "tenant_grant", new AgentToolAuthorizeRequest("tenant_grant", "memory.search")))
        .thenReturn(new AgentToolAuthorizeResponse(true, "memory.search", "ok"));

    InternalAgentPluginController controller = new InternalAgentPluginController(service);

    ApiResponse<AgentToolAuthorizeResponse> response =
        controller.authorize(
            new AgentToolAuthorizeRequest("tenant_grant", "memory.search"), grant());

    assertEquals("memory.search", response.data().toolKey());
    verify(service)
        .authorizeTool(
            "tenant_grant", new AgentToolAuthorizeRequest("tenant_grant", "memory.search"));
  }

  @Test
  void authorizeRejectsTenantOutsideDiagnosisGrant() {
    InternalAgentPluginController controller =
        new InternalAgentPluginController(mock(PluginService.class));

    assertThrows(
        SecurityException.class,
        () ->
            controller.authorize(
                new AgentToolAuthorizeRequest("tenant_forged", "memory.search"), grant()));
  }

  private DiagnosisGrantClaims grant() {
    return new DiagnosisGrantClaims(
        "aiops-server",
        "aegisops-internal-api",
        "tenant_grant",
        "inc_1",
        "trace_1",
        Instant.parse("2026-07-30T08:00:00Z"),
        Instant.parse("2026-07-30T08:05:00Z"));
  }
}
