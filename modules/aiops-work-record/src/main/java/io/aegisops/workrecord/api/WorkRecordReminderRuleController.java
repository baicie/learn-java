package io.aegisops.workrecord.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.ReminderRepository;
import io.aegisops.workrecord.application.service.WorkRecordReminderRuleService;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/reminder-rules")
@PreAuthorize("hasAuthority('work-record:reminder:manage')")
@ConditionalOnProperty(
    prefix = "aiops.runtime",
    name = "app",
    havingValue = "server",
    matchIfMissing = true)
public class WorkRecordReminderRuleController {
  private final WorkRecordReminderRuleService service;

  public WorkRecordReminderRuleController(WorkRecordReminderRuleService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<List<ReminderRepository.ReminderRuleView>> list(
      @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(service.list(TenantContext.requireTenantId(), principal));
  }

  @PostMapping
  public ApiResponse<ReminderRepository.ReminderRuleView> create(
      @RequestBody ReminderRuleRequest request, @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(
        service.create(
            TenantContext.requireTenantId(),
            new WorkRecordReminderRuleService.CreateReminderRule(
                request.name(),
                request.templateId(),
                request.targetType(),
                request.targetJson(),
                request.cutoffTime(),
                request.timeZone()),
            principal));
  }

  @PutMapping("/{id}/enabled")
  public ApiResponse<Map<String, Boolean>> setEnabled(
      @PathVariable String id,
      @RequestBody EnabledRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(
        Map.of(
            "updated",
            service.setEnabled(TenantContext.requireTenantId(), id, request.enabled(), principal)));
  }

  public record ReminderRuleRequest(
      String name,
      String templateId,
      String targetType,
      String targetJson,
      LocalTime cutoffTime,
      String timeZone) {}

  public record EnabledRequest(boolean enabled) {}
}
