package io.aegisops.report;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents/{incidentId}/reports")
@PreAuthorize("hasAuthority('incident:read')")
public class ReportController {
  private final ReportService service;

  public ReportController(ReportService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('incident:write')")
  public ApiResponse<IncidentReportResponse> generate(
      @PathVariable String incidentId,
      @RequestBody(required = false) GenerateIncidentReportRequest request) {
    return ApiResponse.ok(service.generate(TenantContext.requireTenantId(), incidentId, request));
  }

  @GetMapping("/latest")
  public ApiResponse<IncidentReportResponse> latest(@PathVariable String incidentId) {
    return ApiResponse.ok(service.latest(TenantContext.requireTenantId(), incidentId));
  }
}
