package io.aegisops.workrecord;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.api.PageResult;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/records")
public class WorkRecordController {
  private final WorkRecordService service;

  public WorkRecordController(WorkRecordService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('work-record:read:self') or hasAuthority('work-record:read:all')")
  public ApiResponse<PageResult<WorkRecord>> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(service.list(TenantContext.requireTenantId(), user, status, page, size));
  }

  @GetMapping("/{recordId}")
  @PreAuthorize("hasAuthority('work-record:read:self') or hasAuthority('work-record:read:all')")
  public ApiResponse<WorkRecord> get(
      @PathVariable String recordId, @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(service.get(TenantContext.requireTenantId(), recordId, user));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('work-record:write')")
  public ApiResponse<WorkRecord> create(
      @RequestBody CreateWorkRecordRequest request, @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        service.create(
            TenantContext.requireTenantId(), request, user == null ? "system" : user.id()));
  }

  @PutMapping("/{recordId}")
  @PreAuthorize("hasAuthority('work-record:write')")
  public ApiResponse<WorkRecord> update(
      @PathVariable String recordId,
      @RequestBody UpdateWorkRecordRequest request,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(service.update(TenantContext.requireTenantId(), recordId, request, user));
  }

  @DeleteMapping("/{recordId}")
  @PreAuthorize("hasAuthority('work-record:write')")
  public ApiResponse<Void> delete(
      @PathVariable String recordId, @AuthenticationPrincipal UserPrincipal user) {
    service.delete(TenantContext.requireTenantId(), recordId, user);
    return ApiResponse.ok(null);
  }
}
