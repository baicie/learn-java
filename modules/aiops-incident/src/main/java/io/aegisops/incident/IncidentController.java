package io.aegisops.incident;

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
@RequestMapping("/api/incidents")
public class IncidentController {
  private final IncidentService incidentService;

  public IncidentController(IncidentService incidentService) {
    this.incidentService = incidentService;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('incident:read')")
  public ApiResponse<List<IncidentRecord>> list() {
    return ApiResponse.ok(incidentService.list(TenantContext.requireTenantId()));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('incident:read')")
  public ApiResponse<IncidentDetailRecord> detail(@PathVariable String id) {
    return ApiResponse.ok(incidentService.detail(TenantContext.requireTenantId(), id));
  }

  @GetMapping("/{id}/alerts")
  @PreAuthorize("hasAuthority('incident:read')")
  public ApiResponse<List<IncidentAlertRecord>> alerts(@PathVariable String id) {
    return ApiResponse.ok(incidentService.alerts(TenantContext.requireTenantId(), id));
  }

  @GetMapping("/{id}/timeline")
  @PreAuthorize("hasAuthority('incident:read')")
  public ApiResponse<List<IncidentTimelineRecord>> timeline(@PathVariable String id) {
    return ApiResponse.ok(incidentService.timeline(TenantContext.requireTenantId(), id));
  }

  @PostMapping("/aggregate")
  @PreAuthorize("hasAuthority('incident:write')")
  public ApiResponse<IncidentAggregationResponse> aggregate(
      @RequestBody(required = false) IncidentAggregateRequest request) {
    return ApiResponse.ok(
        incidentService.aggregateOpenAlerts(TenantContext.requireTenantId(), request));
  }

  @PostMapping("/{id}/status")
  @PreAuthorize("hasAuthority('incident:write')")
  public ApiResponse<IncidentRecord> updateStatus(
      @PathVariable String id, @RequestBody IncidentStatusRequest request) {
    return ApiResponse.ok(
        incidentService.updateStatus(TenantContext.requireTenantId(), id, request));
  }

  @PostMapping("/{id}/resolve")
  @PreAuthorize("hasAuthority('incident:write')")
  public ApiResponse<IncidentRecord> resolve(@PathVariable String id) {
    return ApiResponse.ok(incidentService.resolve(TenantContext.requireTenantId(), id));
  }

  @PostMapping("/{id}/close")
  @PreAuthorize("hasAuthority('incident:write')")
  public ApiResponse<IncidentRecord> close(@PathVariable String id) {
    return ApiResponse.ok(incidentService.close(TenantContext.requireTenantId(), id));
  }
}
