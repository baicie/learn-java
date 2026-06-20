package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.execution.dto.AgentMemoryCreateRequest;
import io.aegisops.execution.dto.AgentMemoryResponse;
import io.aegisops.execution.dto.AgentMemorySearchRequest;
import io.aegisops.execution.dto.AgentMemorySearchResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal API for Python Agent memory.
 *
 * <p>This API does not execute actions and must be protected by internal network/security policy.
 */
@RestController
public class InternalAgentMemoryController {
  private final AgentMemoryService service;

  public InternalAgentMemoryController(AgentMemoryService service) {
    this.service = service;
  }

  @PostMapping("/internal/agent/memories")
  public ApiResponse<AgentMemoryResponse> create(@RequestBody AgentMemoryCreateRequest request) {
    return ApiResponse.ok(service.createInternal(request));
  }

  @PostMapping("/internal/agent/memories/search")
  public ApiResponse<AgentMemorySearchResponse> search(@RequestBody AgentMemorySearchRequest request) {
    return ApiResponse.ok(service.searchInternal(request));
  }
}
