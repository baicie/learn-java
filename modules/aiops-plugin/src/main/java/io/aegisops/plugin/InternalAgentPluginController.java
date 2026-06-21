package io.aegisops.plugin;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.plugin.dto.AgentToolAuthorizeRequest;
import io.aegisops.plugin.dto.AgentToolAuthorizeResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalAgentPluginController {
  private final PluginService service;

  public InternalAgentPluginController(PluginService service) {
    this.service = service;
  }

  @PostMapping("/internal/agent/plugins/tools/authorize")
  public ApiResponse<AgentToolAuthorizeResponse> authorize(
      @RequestBody AgentToolAuthorizeRequest request) {
    return ApiResponse.ok(service.authorizeTool(request));
  }
}
