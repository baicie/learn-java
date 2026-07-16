package io.aegisops.workrecord.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.workrecord.application.command.WorkRecordUserOption;
import io.aegisops.workrecord.application.service.WorkRecordUserLookupService;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/users")
public class WorkRecordUserLookupController {
  private final WorkRecordUserLookupService service;

  public WorkRecordUserLookupController(WorkRecordUserLookupService service) {
    this.service = service;
  }

  @GetMapping("/display-names")
  @PreAuthorize("hasAuthority('work-record:read:all') or hasAuthority('work-record:read:self')")
  public ApiResponse<Map<String, String>> displayNames(
      @RequestParam(required = false) List<String> ids) {
    return ApiResponse.ok(
        service.displayNames(TenantContext.requireTenantId(), ids == null ? List.of() : ids));
  }

  @GetMapping("/options")
  @PreAuthorize("hasAuthority('work-record:read:all') or hasAuthority('work-record:read:self')")
  public ApiResponse<List<WorkRecordUserOption>> options() {
    return ApiResponse.ok(service.activeOptions(TenantContext.requireTenantId()));
  }
}
