package io.aegisops.workrecord.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.WorkflowRepository.ApprovalTaskView;
import io.aegisops.workrecord.application.port.WorkflowRepository.SlaInstanceView;
import io.aegisops.workrecord.application.service.WorkRecordLifecycleCoordinator;
import io.aegisops.workrecord.application.service.WorkflowConfigurationService;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/workflow")
public class WorkRecordWorkflowController {
  private final WorkflowConfigurationService configuration;
  private final WorkRecordLifecycleCoordinator lifecycle;

  public WorkRecordWorkflowController(
      WorkflowConfigurationService configuration, WorkRecordLifecycleCoordinator lifecycle) {
    this.configuration = configuration;
    this.lifecycle = lifecycle;
  }

  @PostMapping("/approvals")
  @PreAuthorize("hasAuthority('work-record:approval:manage')")
  public ApiResponse<Map<String, String>> approval(
      @RequestBody ApprovalRequest r, @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        Map.of(
            "id",
            configuration.createApproval(
                TenantContext.requireTenantId(),
                new WorkflowConfigurationService.ApprovalConfiguration(
                    r.templateId(),
                    r.name(),
                    r.approverType(),
                    r.approverValue(),
                    r.timeoutMinutes()),
                user)));
  }

  @PostMapping("/approval-tasks/{id}/act")
  @PreAuthorize("hasAuthority('work-record:approval:act')")
  public ApiResponse<Map<String, String>> act(
      @PathVariable String id,
      @RequestBody ActionRequest r,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        Map.of(
            "status",
            lifecycle.act(TenantContext.requireTenantId(), id, r.approved(), r.comment(), user)));
  }

  @GetMapping("/approval-tasks")
  @PreAuthorize("hasAuthority('work-record:approval:act')")
  public ApiResponse<List<ApprovalTaskView>> pendingTasks(
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(lifecycle.pendingTasks(TenantContext.requireTenantId(), user));
  }

  @GetMapping("/records/{recordId}/sla")
  @PreAuthorize("hasAuthority('work-record:read:self') or hasAuthority('work-record:read:all')")
  public ApiResponse<List<SlaInstanceView>> sla(
      @PathVariable String recordId, @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(lifecycle.slaInstances(TenantContext.requireTenantId(), recordId, user));
  }

  @PostMapping("/sla-policies")
  @PreAuthorize("hasAuthority('work-record:sla:manage')")
  public ApiResponse<Map<String, String>> sla(
      @RequestBody SlaRequest r, @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        Map.of(
            "id",
            configuration.createSla(
                TenantContext.requireTenantId(),
                new WorkflowConfigurationService.SlaConfiguration(
                    r.templateId(),
                    r.name(),
                    r.startEvent(),
                    r.stopEvent(),
                    r.targetMinutes(),
                    r.calendarAware(),
                    r.severity()),
                user)));
  }

  public record ApprovalRequest(
      String templateId,
      String name,
      String approverType,
      String approverValue,
      Integer timeoutMinutes) {}

  public record ActionRequest(boolean approved, String comment) {}

  public record SlaRequest(
      String templateId,
      String name,
      String startEvent,
      String stopEvent,
      int targetMinutes,
      boolean calendarAware,
      String severity) {}
}
