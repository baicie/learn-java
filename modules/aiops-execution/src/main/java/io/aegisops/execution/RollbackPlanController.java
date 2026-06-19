package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.execution.dto.ExecutionRunResponse;
import io.aegisops.execution.dto.RollbackDecisionRequest;
import io.aegisops.execution.dto.RollbackExecutionCreateRequest;
import io.aegisops.execution.dto.RollbackPlanCreateRequest;
import io.aegisops.execution.dto.RollbackPlanResponse;
import io.aegisops.execution.dto.RollbackPlanSubmitRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RollbackPlanController {
  private final RollbackPlanService planService;
  private final RollbackApprovalService approvalService;
  private final RollbackExecutionService executionService;

  public RollbackPlanController(
      RollbackPlanService planService,
      RollbackApprovalService approvalService,
      RollbackExecutionService executionService) {
    this.planService = planService;
    this.approvalService = approvalService;
    this.executionService = executionService;
  }

  @PostMapping("/api/executions/{executionId}/rollback-plans")
  public ApiResponse<RollbackPlanResponse> create(
      @PathVariable String executionId, @RequestBody RollbackPlanCreateRequest request) {
    return ApiResponse.ok(
        planService.create(TenantContext.requireTenantId(), executionId, request));
  }

  @GetMapping("/api/executions/{executionId}/rollback-plans/latest")
  public ApiResponse<RollbackPlanResponse> latest(@PathVariable String executionId) {
    return ApiResponse.ok(
        planService.latestBySourceExecution(TenantContext.requireTenantId(), executionId));
  }

  @GetMapping("/api/rollback-plans/{rollbackPlanId}")
  public ApiResponse<RollbackPlanResponse> get(@PathVariable String rollbackPlanId) {
    return ApiResponse.ok(planService.get(TenantContext.requireTenantId(), rollbackPlanId));
  }

  @PostMapping("/api/rollback-plans/{rollbackPlanId}/submit")
  public ApiResponse<RollbackPlanResponse> submit(
      @PathVariable String rollbackPlanId, @RequestBody RollbackPlanSubmitRequest request) {
    return ApiResponse.ok(
        approvalService.submit(TenantContext.requireTenantId(), rollbackPlanId, request));
  }

  @PostMapping("/api/rollback-plans/{rollbackPlanId}/approve")
  public ApiResponse<RollbackPlanResponse> approve(
      @PathVariable String rollbackPlanId, @RequestBody RollbackDecisionRequest request) {
    return ApiResponse.ok(
        approvalService.approve(TenantContext.requireTenantId(), rollbackPlanId, request));
  }

  @PostMapping("/api/rollback-plans/{rollbackPlanId}/reject")
  public ApiResponse<RollbackPlanResponse> reject(
      @PathVariable String rollbackPlanId, @RequestBody RollbackDecisionRequest request) {
    return ApiResponse.ok(
        approvalService.reject(TenantContext.requireTenantId(), rollbackPlanId, request));
  }

  @PostMapping("/api/rollback-plans/{rollbackPlanId}/cancel")
  public ApiResponse<RollbackPlanResponse> cancel(@PathVariable String rollbackPlanId) {
    return ApiResponse.ok(planService.cancel(TenantContext.requireTenantId(), rollbackPlanId));
  }

  @PostMapping("/api/rollback-plans/{rollbackPlanId}/executions")
  public ApiResponse<ExecutionRunResponse> createExecution(
      @PathVariable String rollbackPlanId, @RequestBody RollbackExecutionCreateRequest request) {
    return ApiResponse.ok(
        executionService.createExecution(TenantContext.requireTenantId(), rollbackPlanId, request));
  }
}
