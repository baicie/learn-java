package io.aegisops.rca;

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
@RequestMapping("/api/incidents/{incidentId}/rca")
public class RcaController {
  private final RcaService rcaService;

  public RcaController(RcaService rcaService) {
    this.rcaService = rcaService;
  }

  @GetMapping("/latest")
  @PreAuthorize("hasAuthority('incident:read')")
  public ApiResponse<RcaAnalysisResponse> latest(@PathVariable String incidentId) {
    return ApiResponse.ok(rcaService.latest(TenantContext.requireTenantId(), incidentId));
  }

  @PostMapping("/analyze")
  @PreAuthorize("hasAuthority('incident:diagnose')")
  public ApiResponse<RcaAnalysisResponse> analyze(
      @PathVariable String incidentId, @RequestBody(required = false) RcaAnalyzeRequest request) {
    return ApiResponse.ok(rcaService.analyze(TenantContext.requireTenantId(), incidentId, request));
  }
}
