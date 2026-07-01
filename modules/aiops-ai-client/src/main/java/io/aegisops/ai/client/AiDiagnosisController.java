package io.aegisops.ai.client;

import io.aegisops.ai.client.dto.AgentRunDetailResponse;
import io.aegisops.ai.client.dto.AiDiagnoseRequest;
import io.aegisops.ai.client.dto.AiDiagnosisResponse;
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
@RequestMapping("/api/incidents/{incidentId}/ai")
public class AiDiagnosisController {
  private final AiDiagnosisService diagnosisService;

  public AiDiagnosisController(AiDiagnosisService diagnosisService) {
    this.diagnosisService = diagnosisService;
  }

  @GetMapping("/latest")
  @PreAuthorize("hasAuthority('incident:read')")
  public ApiResponse<AiDiagnosisResponse> latest(@PathVariable String incidentId) {
    return ApiResponse.ok(diagnosisService.latest(TenantContext.requireTenantId(), incidentId));
  }

  @PostMapping("/diagnose")
  @PreAuthorize("hasAuthority('incident:diagnose')")
  public ApiResponse<AiDiagnosisResponse> diagnose(
      @PathVariable String incidentId, @RequestBody(required = false) AiDiagnoseRequest request) {
    return ApiResponse.ok(
        diagnosisService.diagnose(TenantContext.requireTenantId(), incidentId, request));
  }

  @GetMapping("/runs/latest")
  @PreAuthorize("hasAuthority('incident:read')")
  public ApiResponse<AgentRunDetailResponse> latestRun(@PathVariable String incidentId) {
    return ApiResponse.ok(diagnosisService.latestRun(TenantContext.requireTenantId(), incidentId));
  }
}
