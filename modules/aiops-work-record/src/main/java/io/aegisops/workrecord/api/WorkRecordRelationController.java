package io.aegisops.workrecord.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.service.WorkRecordRelationService;
import io.aegisops.workrecord.domain.model.RelationType;
import io.aegisops.workrecord.domain.model.WorkRecordRelation;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/records/{recordId}/relations")
@ConditionalOnProperty(
    prefix = "aiops.runtime",
    name = "app",
    havingValue = "server",
    matchIfMissing = true)
public class WorkRecordRelationController {
  private final WorkRecordRelationService service;

  public WorkRecordRelationController(WorkRecordRelationService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('work-record:read:self') or hasAuthority('work-record:read:all')")
  public ApiResponse<List<WorkRecordRelation>> list(
      @PathVariable String recordId, @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(service.list(TenantContext.requireTenantId(), recordId, principal));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('work-record:relation')")
  public ApiResponse<WorkRecordRelation> create(
      @PathVariable String recordId,
      @RequestBody RelationRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(
        service.create(
            TenantContext.requireTenantId(),
            recordId,
            RelationType.from(request.relationType()),
            request.targetId(),
            principal));
  }

  @DeleteMapping("/{relationId}")
  @PreAuthorize("hasAuthority('work-record:relation')")
  public ApiResponse<Map<String, Boolean>> delete(
      @PathVariable String recordId,
      @PathVariable String relationId,
      @AuthenticationPrincipal UserPrincipal principal) {
    service.delete(TenantContext.requireTenantId(), recordId, relationId, principal);
    return ApiResponse.ok(Map.of("deleted", true));
  }

  public record RelationRequest(String relationType, String targetId) {}
}
