package io.aegisops.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.plugin.dto.AgentToolAuthorizeRequest;
import io.aegisops.plugin.dto.AgentToolAuthorizeResponse;
import org.junit.jupiter.api.Test;

class InternalAgentPluginControllerTest {
  @Test
  void authorizeUsesTenantContextInsteadOfBodyTenant() {
    PluginService service = mock(PluginService.class);
    when(service.authorizeTool(
            "tenant_header", new AgentToolAuthorizeRequest("tenant_body", "memory.search")))
        .thenReturn(new AgentToolAuthorizeResponse(true, "memory.search", "ok"));

    InternalAgentPluginController controller = new InternalAgentPluginController(service);

    TenantContext.setTenantId("tenant_header");
    try {
      ApiResponse<AgentToolAuthorizeResponse> response =
          controller.authorize(new AgentToolAuthorizeRequest("tenant_body", "memory.search"));

      assertEquals("memory.search", response.data().toolKey());
      verify(service)
          .authorizeTool(
              "tenant_header", new AgentToolAuthorizeRequest("tenant_body", "memory.search"));
    } finally {
      TenantContext.clear();
    }
  }
}
