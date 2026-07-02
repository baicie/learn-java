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
  private final ZabbixEvidenceCollectorService collectorService;
  private final EvidenceOrchestrationService orchestrationService;
  private final EvidenceCollectionTaskRepository taskRepository;

  public EvidenceController(
      ZabbixEvidenceCollectorService collectorService,
      EvidenceOrchestrationService orchestrationService,
      EvidenceCollectionTaskRepository taskRepository) {
    this.collectorService = collectorService;
    this.orchestrationService = orchestrationService;
    this.taskRepository = taskRepository;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('incident:read')")
  public ApiResponse<List<DiagnosisEvidenceRecord>> list(@PathVariable String incidentId) {
    return ApiResponse.ok(collectorService.list(TenantContext.requireTenantId(), incidentId));
  }

  @PostMapping("/collect")
  @PreAuthorize("hasAuthority('incident:diagnose')")
  public ApiResponse<EvidenceCollectResponse> collect(
      @PathVariable String incidentId,
      @RequestBody(required = false) EvidenceCollectRequest request) {
    return ApiResponse.ok(
        orchestrationService.collect(TenantContext.requireTenantId(), incidentId, request));
  }

  @GetMapping("/tasks")
  @PreAuthorize("hasAuthority('incident:read')")
  public ApiResponse<List<EvidenceCollectionTaskRecord>> tasks(@PathVariable String incidentId) {
    return ApiResponse.ok(taskRepository.listByIncident(TenantContext.requireTenantId(), incidentId));
  }
}
