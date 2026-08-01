package io.aegisops.plugin;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.security.DiagnosisGrantAuthorization;
import io.aegisops.common.security.DiagnosisGrantClaims;
import io.aegisops.plugin.dto.AgentToolAuthorizeRequest;
import io.aegisops.plugin.dto.AgentToolAuthorizeResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "aiops.internal-agent-api", name = "enabled", havingValue = "true")
public class InternalAgentPluginController {
  private final PluginService service;

  public InternalAgentPluginController(PluginService service) {
    this.service = service;
  }

  @PostMapping("/internal/agent/plugins/tools/authorize")
  public ApiResponse<AgentToolAuthorizeResponse> authorize(
      @RequestBody AgentToolAuthorizeRequest request,
      @RequestAttribute(DiagnosisGrantAuthorization.REQUEST_ATTRIBUTE)
          DiagnosisGrantClaims claims) {
    String tenantId = DiagnosisGrantAuthorization.requireTenant(claims, request.tenantId());
    return ApiResponse.ok(service.authorizeTool(tenantId, request));
  }
}
