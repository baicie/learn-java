package io.aegisops.runbook;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.runbook.dto.AutomationPlanResponse;
import io.aegisops.runbook.dto.RecommendPlanRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for automation plan recommendation and lookup. */
@RestController
public class AutomationPlanController {
  private final AutomationPlanService service;

  public AutomationPlanController(AutomationPlanService service) {
    this.service = service;
  }

  @PostMapping("/api/incidents/{incidentId}/automation-plans/recommend")
  @PreAuthorize("hasAuthority('automation:execute')")
  public ApiResponse<AutomationPlanResponse> recommend(
      @PathVariable String incidentId,
      @RequestBody(required = false) RecommendPlanRequest request) {
    return ApiResponse.ok(service.recommend(TenantContext.requireTenantId(), incidentId, request));
  }

  @GetMapping("/api/incidents/{incidentId}/automation-plans/latest")
  @PreAuthorize("hasAuthority('automation:read')")
  public ApiResponse<AutomationPlanResponse> latest(@PathVariable String incidentId) {
    return ApiResponse.ok(service.latestPlan(TenantContext.requireTenantId(), incidentId));
  }

  @GetMapping("/api/automation-plans/{planId}")
  @PreAuthorize("hasAuthority('automation:read')")
  public ApiResponse<AutomationPlanResponse> get(@PathVariable String planId) {
    return ApiResponse.ok(service.getPlan(TenantContext.requireTenantId(), planId));
  }
}
