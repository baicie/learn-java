package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.security.DiagnosisGrantAuthorization;
import io.aegisops.common.security.DiagnosisGrantClaims;
import io.aegisops.execution.dto.AgentMemoryCreateRequest;
import io.aegisops.execution.dto.AgentMemoryResponse;
import io.aegisops.execution.dto.AgentMemorySearchRequest;
import io.aegisops.execution.dto.AgentMemorySearchResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
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
  public ApiResponse<AgentMemoryResponse> create(
      @RequestBody AgentMemoryCreateRequest request,
      @RequestAttribute(DiagnosisGrantAuthorization.REQUEST_ATTRIBUTE)
          DiagnosisGrantClaims claims) {
    authorizeScope(claims, request.tenantId(), request.scopeType(), request.scopeId());
    return ApiResponse.ok(service.createInternal(request));
  }

  @PostMapping("/internal/agent/memories/search")
  public ApiResponse<AgentMemorySearchResponse> search(
      @RequestBody AgentMemorySearchRequest request,
      @RequestAttribute(DiagnosisGrantAuthorization.REQUEST_ATTRIBUTE)
          DiagnosisGrantClaims claims) {
    authorizeScope(claims, request.tenantId(), request.scopeType(), request.scopeId());
    return ApiResponse.ok(service.searchInternal(request));
  }

  private void authorizeScope(
      DiagnosisGrantClaims claims, String tenantId, String scopeType, String scopeId) {
    if (scopeType != null && "incident".equalsIgnoreCase(scopeType.trim())) {
      DiagnosisGrantAuthorization.requireIncident(claims, tenantId, scopeId);
      return;
    }
    DiagnosisGrantAuthorization.requireTenant(claims, tenantId);
  }
}
