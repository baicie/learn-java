package io.aegisops.runbook;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.runbook.dto.ApprovalDecisionRequest;
import io.aegisops.runbook.dto.ApprovalResponse;
import io.aegisops.runbook.dto.SubmitApprovalRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApprovalController {
  private final ApprovalService service;

  public ApprovalController(ApprovalService service) {
    this.service = service;
  }

  @PostMapping("/api/automation-plans/{planId}/submit")
  @PreAuthorize("hasAuthority('automation:execute')")
  public ApiResponse<ApprovalResponse> submit(
      @PathVariable String planId, @RequestBody(required = false) SubmitApprovalRequest request) {
    return ApiResponse.ok(service.submit(TenantContext.requireTenantId(), planId, request));
  }

  @GetMapping("/api/automation-plans/{planId}/approval/latest")
  @PreAuthorize("hasAuthority('automation:read')")
  public ApiResponse<ApprovalResponse> latestByPlan(@PathVariable String planId) {
    return ApiResponse.ok(service.latestByPlan(TenantContext.requireTenantId(), planId));
  }

  @PostMapping("/api/automation-approvals/{approvalId}/approve")
  @PreAuthorize("hasAuthority('automation:approve')")
  public ApiResponse<ApprovalResponse> approve(
      @PathVariable String approvalId, @RequestBody ApprovalDecisionRequest request) {
    return ApiResponse.ok(service.approve(TenantContext.requireTenantId(), approvalId, request));
  }

  @PostMapping("/api/automation-approvals/{approvalId}/reject")
  @PreAuthorize("hasAuthority('automation:approve')")
  public ApiResponse<ApprovalResponse> reject(
      @PathVariable String approvalId, @RequestBody ApprovalDecisionRequest request) {
    return ApiResponse.ok(service.reject(TenantContext.requireTenantId(), approvalId, request));
  }

  @PostMapping("/api/automation-approvals/{approvalId}/cancel")
  @PreAuthorize("hasAuthority('automation:execute')")
  public ApiResponse<ApprovalResponse> cancel(
      @PathVariable String approvalId, @RequestBody ApprovalDecisionRequest request) {
    return ApiResponse.ok(service.cancel(TenantContext.requireTenantId(), approvalId, request));
  }

  @GetMapping("/api/automation-approvals/{approvalId}")
  @PreAuthorize("hasAuthority('automation:read')")
  public ApiResponse<ApprovalResponse> get(@PathVariable String approvalId) {
    return ApiResponse.ok(service.getApproval(TenantContext.requireTenantId(), approvalId));
  }
}
