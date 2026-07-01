package io.aegisops.evidence;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents/{incidentId}/evidence")
public class EvidenceController {
  private final ZabbixEvidenceCollectorService service;

  public EvidenceController(ZabbixEvidenceCollectorService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('incident:read')")
  public ApiResponse<List<DiagnosisEvidenceRecord>> list(@PathVariable String incidentId) {
    return ApiResponse.ok(service.list(TenantContext.requireTenantId(), incidentId));
  }

  @PostMapping("/zabbix/collect")
  @PreAuthorize("hasAuthority('incident:diagnose')")
  public ApiResponse<EvidenceCollectResponse> collectZabbix(
      @PathVariable String incidentId,
      @RequestBody(required = false) EvidenceCollectRequest request) {
    return ApiResponse.ok(service.collect(TenantContext.requireTenantId(), incidentId, request));
  }
}
