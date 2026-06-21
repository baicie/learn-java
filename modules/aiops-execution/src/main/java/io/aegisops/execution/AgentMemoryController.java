package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.execution.dto.AgentMemoryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public read/archive API for Agent Memory. */
@RestController
public class AgentMemoryController {
  private final AgentMemoryService service;

  public AgentMemoryController(AgentMemoryService service) {
    this.service = service;
  }

  @GetMapping("/api/agent-memories/{memoryId}")
  public ApiResponse<AgentMemoryResponse> get(@PathVariable String memoryId) {
    return ApiResponse.ok(service.get(TenantContext.requireTenantId(), memoryId));
  }

  @PostMapping("/api/agent-memories/{memoryId}/archive")
  public ApiResponse<AgentMemoryResponse> archive(@PathVariable String memoryId) {
    return ApiResponse.ok(service.archive(TenantContext.requireTenantId(), memoryId));
  }
}
