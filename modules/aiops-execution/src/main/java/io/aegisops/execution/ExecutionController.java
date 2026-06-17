package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.execution.dto.ExecutionCreateRequest;
import io.aegisops.execution.dto.ExecutionRunResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnableConfigurationProperties(ExecutionProperties.class)
@ConditionalOnProperty(
    prefix = "aiops.execution",
    name = "api-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ExecutionController {
  private final ExecutionRequestService service;

  public ExecutionController(ExecutionRequestService service) {
    this.service = service;
  }

  @PostMapping("/api/automation-plans/{planId}/executions")
  public ApiResponse<ExecutionRunResponse> create(
      @PathVariable String planId, @RequestBody(required = false) ExecutionCreateRequest request) {
    return ApiResponse.ok(
        service.createExecution(TenantContext.requireTenantId(), planId, request));
  }

  @GetMapping("/api/executions/{executionId}")
  public ApiResponse<ExecutionRunResponse> get(@PathVariable String executionId) {
    return ApiResponse.ok(service.getExecution(TenantContext.requireTenantId(), executionId));
  }

  @GetMapping("/api/automation-plans/{planId}/executions/latest")
  public ApiResponse<ExecutionRunResponse> latestByPlan(@PathVariable String planId) {
    return ApiResponse.ok(service.latestByPlan(TenantContext.requireTenantId(), planId));
  }

  @PostMapping("/api/executions/{executionId}/cancel")
  public ApiResponse<ExecutionRunResponse> cancel(@PathVariable String executionId) {
    return ApiResponse.ok(service.cancel(TenantContext.requireTenantId(), executionId));
  }
}
