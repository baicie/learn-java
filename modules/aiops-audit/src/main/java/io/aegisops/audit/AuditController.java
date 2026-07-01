package io.aegisops.audit;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
  public ApiResponse<List<AuditLog>> list() {
    return ApiResponse.ok(service.listRecent(TenantContext.requireTenantId()));
  }
}
