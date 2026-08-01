package io.aegisops.workrecord.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.service.WorkRecordNotificationService;
import io.aegisops.workrecord.domain.model.WorkRecordNotification;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/notifications")
@PreAuthorize("isAuthenticated()")
@ConditionalOnProperty(
    prefix = "aiops.runtime",
    name = "app",
    havingValue = "app",
    matchIfMissing = true)
public class WorkRecordNotificationController {
  private final WorkRecordNotificationService service;

  public WorkRecordNotificationController(WorkRecordNotificationService service) {
    this.service = service;
  }

  @GetMapping("/unread")
  public ApiResponse<List<WorkRecordNotification>> unread(
      @RequestParam(defaultValue = "50") int limit,
      @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(service.unread(TenantContext.requireTenantId(), limit, principal));
  }

  @PostMapping("/{id}/read")
  public ApiResponse<Map<String, Boolean>> markRead(
      @PathVariable String id, @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(
        Map.of("updated", service.markRead(TenantContext.requireTenantId(), id, principal)));
  }
}
