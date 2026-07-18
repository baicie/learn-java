package io.aegisops.integration.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.integration.api.dto.IngestBatchResponse;
import io.aegisops.integration.api.dto.IngestResponse;
import io.aegisops.integration.application.OperationsIngestService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations")
public class OperationsIngestController {
  private final OperationsIngestService service;

  public OperationsIngestController(OperationsIngestService service) {
    this.service = service;
  }

  @PostMapping("/opentelemetry/{datasourceId}/signals")
  public ApiResponse<IngestBatchResponse> otel(
      @PathVariable String datasourceId, @RequestBody JsonNode payload) {
    return ApiResponse.ok(
        service.ingestOtelBatch(TenantContext.requireTenantId(), datasourceId, payload));
  }

  @PostMapping("/rum/{datasourceId}/events")
  public ApiResponse<IngestResponse> rum(
      @PathVariable String datasourceId, @RequestBody JsonNode payload) {
    return ApiResponse.ok(
        service.ingestRum(TenantContext.requireTenantId(), datasourceId, payload));
  }

  @PostMapping("/changes/{datasourceId}/events")
  public ApiResponse<IngestResponse> change(
      @PathVariable String datasourceId, @RequestBody JsonNode request) {
    return ApiResponse.ok(
        service.ingestChangePayload(TenantContext.requireTenantId(), datasourceId, request));
  }
}
