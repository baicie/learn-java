package io.aegisops.audit;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditController {
  private final AuditQueryService service;

  public AuditController(AuditQueryService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('audit:read')")
  public ApiResponse<List<AuditEvent>> list(
      @RequestParam(required = false) String resourceType,
      @RequestParam(required = false) String resourceId,
      @RequestParam(required = false) Integer limit) {
    String tenantId = TenantContext.requireTenantId();

    boolean hasType = resourceType != null && !resourceType.isBlank();
    boolean hasId = resourceId != null && !resourceId.isBlank();

    if (hasType != hasId) {
      throw new IllegalArgumentException("resourceType and resourceId must be provided together");
    }

    if (hasType) {
      return ApiResponse.ok(service.listByResource(tenantId, resourceType, resourceId, limit));
    }

    return ApiResponse.ok(service.listRecent(tenantId));
  }
}
