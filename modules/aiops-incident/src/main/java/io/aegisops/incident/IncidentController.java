package io.aegisops.incident;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {
    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping
    public ApiResponse<List<IncidentRecord>> list() {
        return ApiResponse.ok(incidentService.list(TenantContext.requireTenantId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<IncidentDetailRecord> detail(@PathVariable String id) {
        return ApiResponse.ok(incidentService.detail(TenantContext.requireTenantId(), id));
    }

    @GetMapping("/{id}/alerts")
    public ApiResponse<List<IncidentAlertRecord>> alerts(@PathVariable String id) {
        return ApiResponse.ok(incidentService.alerts(TenantContext.requireTenantId(), id));
    }

    @GetMapping("/{id}/timeline")
    public ApiResponse<List<IncidentTimelineRecord>> timeline(@PathVariable String id) {
        return ApiResponse.ok(incidentService.timeline(TenantContext.requireTenantId(), id));
    }

    @PostMapping("/aggregate")
    public ApiResponse<IncidentAggregationResponse> aggregate(@RequestBody(required = false) IncidentAggregateRequest request) {
        return ApiResponse.ok(incidentService.aggregateOpenAlerts(TenantContext.requireTenantId(), request));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<IncidentRecord> updateStatus(@PathVariable String id, @RequestBody IncidentStatusRequest request) {
        return ApiResponse.ok(incidentService.updateStatus(TenantContext.requireTenantId(), id, request));
    }

    @PostMapping("/{id}/resolve")
    public ApiResponse<IncidentRecord> resolve(@PathVariable String id) {
        return ApiResponse.ok(incidentService.resolve(TenantContext.requireTenantId(), id));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<IncidentRecord> close(@PathVariable String id) {
        return ApiResponse.ok(incidentService.close(TenantContext.requireTenantId(), id));
    }
}
