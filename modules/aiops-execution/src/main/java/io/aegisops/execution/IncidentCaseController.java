package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.execution.dto.IncidentCaseCreateFromPostmortemRequest;
import io.aegisops.execution.dto.IncidentCasePublishRequest;
import io.aegisops.execution.dto.IncidentCaseResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IncidentCaseController {
  private final IncidentCaseService service;

  public IncidentCaseController(IncidentCaseService service) {
    this.service = service;
  }

  @PostMapping("/api/postmortems/{postmortemId}/cases")
  public ApiResponse<IncidentCaseResponse> createFromPostmortem(
      @PathVariable String postmortemId,
      @RequestBody(required = false) IncidentCaseCreateFromPostmortemRequest request) {
    return ApiResponse.ok(
        service.createFromPostmortem(TenantContext.requireTenantId(), postmortemId, request));
  }

  @GetMapping("/api/incidents/{incidentId}/cases/latest")
  public ApiResponse<IncidentCaseResponse> latestByIncident(@PathVariable String incidentId) {
    return ApiResponse.ok(service.latestByIncident(TenantContext.requireTenantId(), incidentId));
  }

  @GetMapping("/api/incident-cases/{caseId}")
  public ApiResponse<IncidentCaseResponse> get(@PathVariable String caseId) {
    return ApiResponse.ok(service.get(TenantContext.requireTenantId(), caseId));
  }

  @GetMapping("/api/incident-cases")
  public ApiResponse<List<IncidentCaseResponse>> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String tag,
      @RequestParam(defaultValue = "20") int limit) {
    return ApiResponse.ok(service.list(TenantContext.requireTenantId(), status, tag, limit));
  }

  @PostMapping("/api/incident-cases/{caseId}/publish")
  public ApiResponse<IncidentCaseResponse> publish(
      @PathVariable String caseId, @RequestBody IncidentCasePublishRequest request) {
    return ApiResponse.ok(service.publish(TenantContext.requireTenantId(), caseId, request));
  }

  @PostMapping("/api/incident-cases/{caseId}/archive")
  public ApiResponse<IncidentCaseResponse> archive(@PathVariable String caseId) {
    return ApiResponse.ok(service.archive(TenantContext.requireTenantId(), caseId));
  }
}
