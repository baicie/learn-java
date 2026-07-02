package io.aegisops.inspection;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inspections/tasks")
public class InspectionController {
  private final InspectionService service;

  public InspectionController(InspectionService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('incident:read')")
  public ApiResponse<List<InspectionTaskRecord>> list() {
    return ApiResponse.ok(service.list(TenantContext.requireTenantId()));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('incident:write')")
  public ApiResponse<InspectionTaskRecord> create(
      @RequestBody CreateInspectionTaskRequest request,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        service.create(TenantContext.requireTenantId(), request, user == null ? "system" : user.id()));
  }
}
