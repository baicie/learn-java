package io.aegisops.rca;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/incidents/{incidentId}/rca")
public class RcaController {
    private final RcaService rcaService;

    public RcaController(RcaService rcaService) {
        this.rcaService = rcaService;
    }

    @GetMapping("/latest")
    public ApiResponse<RcaAnalysisResponse> latest(@PathVariable String incidentId) {
        return ApiResponse.ok(rcaService.latest(TenantContext.requireTenantId(), incidentId));
    }

    @PostMapping("/analyze")
    public ApiResponse<RcaAnalysisResponse> analyze(
            @PathVariable String incidentId,
            @RequestBody(required = false) RcaAnalyzeRequest request
    ) {
        return ApiResponse.ok(rcaService.analyze(TenantContext.requireTenantId(), incidentId, request));
    }
}
