package io.aegisops.workrecord.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.service.WorkRecordHandoverService;
import io.aegisops.workrecord.domain.model.WorkRecordHandover;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/handovers")
@PreAuthorize("hasAuthority('work-record:handover')")
@ConditionalOnProperty(
    prefix = "aiops.runtime",
    name = "app",
    havingValue = "server",
    matchIfMissing = true)
public class WorkRecordHandoverController {
  private final WorkRecordHandoverService service;

  public WorkRecordHandoverController(WorkRecordHandoverService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<List<WorkRecordHandover>> list(
      @RequestParam(defaultValue = "50") int limit,
      @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(service.list(TenantContext.requireTenantId(), limit, principal));
  }

  @PostMapping
  public ApiResponse<WorkRecordHandover> create(
      @RequestBody HandoverRequest request, @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(
        service.create(
            TenantContext.requireTenantId(),
            new WorkRecordHandoverService.CreateHandoverCommand(
                principal.id(),
                request.toUserId(),
                request.shiftStart(),
                request.shiftEnd(),
                request.summary(),
                request.recordIds(),
                request.relationIds()),
            principal));
  }

  @PostMapping("/{id}/submit")
  public ApiResponse<WorkRecordHandover> submit(
      @PathVariable String id,
      @RequestParam int rowVersion,
      @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(
        service.submit(TenantContext.requireTenantId(), id, rowVersion, principal));
  }

  @PostMapping("/{id}/accept")
  public ApiResponse<WorkRecordHandover> accept(
      @PathVariable String id,
      @RequestParam int rowVersion,
      @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(
        service.accept(TenantContext.requireTenantId(), id, rowVersion, principal));
  }

  @PostMapping("/{id}/complete")
  public ApiResponse<WorkRecordHandover> complete(
      @PathVariable String id,
      @RequestParam int rowVersion,
      @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(
        service.complete(TenantContext.requireTenantId(), id, rowVersion, principal));
  }

  public record HandoverRequest(
      String toUserId,
      OffsetDateTime shiftStart,
      OffsetDateTime shiftEnd,
      String summary,
      List<String> recordIds,
      List<String> relationIds) {}
}
