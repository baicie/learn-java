package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.execution.dto.ExecutionAuditEventResponse;
import io.aegisops.execution.dto.ExecutionReportGenerateRequest;
import io.aegisops.execution.dto.ExecutionReportResponse;
import io.aegisops.execution.dto.ExecutionVerificationCreateRequest;
import io.aegisops.execution.dto.ExecutionVerificationResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExecutionReportController {
  private final ExecutionReportService service;

  public ExecutionReportController(ExecutionReportService service) {
    this.service = service;
  }

  @PostMapping("/api/executions/{executionId}/reports/generate")
  public ApiResponse<ExecutionReportResponse> generate(
      @PathVariable String executionId,
      @RequestBody(required = false) ExecutionReportGenerateRequest request) {
    return ApiResponse.ok(service.generate(TenantContext.requireTenantId(), executionId, request));
  }

  @GetMapping("/api/executions/{executionId}/reports/latest")
  public ApiResponse<ExecutionReportResponse> latest(@PathVariable String executionId) {
    return ApiResponse.ok(service.latestByExecution(TenantContext.requireTenantId(), executionId));
  }

  @GetMapping("/api/execution-reports/{reportId}")
  public ApiResponse<ExecutionReportResponse> get(@PathVariable String reportId) {
    return ApiResponse.ok(service.get(TenantContext.requireTenantId(), reportId));
  }

  @GetMapping(value = "/api/execution-reports/{reportId}/markdown", produces = "text/markdown")
  public String markdown(@PathVariable String reportId) {
    return service.markdown(TenantContext.requireTenantId(), reportId);
  }

  @PostMapping("/api/executions/{executionId}/verifications")
  public ApiResponse<ExecutionVerificationResponse> createVerification(
      @PathVariable String executionId, @RequestBody ExecutionVerificationCreateRequest request) {
    return ApiResponse.ok(
        service.createVerification(TenantContext.requireTenantId(), executionId, request));
  }

  @GetMapping("/api/executions/{executionId}/verifications")
  public ApiResponse<List<ExecutionVerificationResponse>> listVerifications(
      @PathVariable String executionId) {
    return ApiResponse.ok(service.listVerifications(TenantContext.requireTenantId(), executionId));
  }

  @GetMapping("/api/executions/{executionId}/audit-events")
  public ApiResponse<List<ExecutionAuditEventResponse>> auditEvents(
      @PathVariable String executionId) {
    return ApiResponse.ok(service.listAuditEvents(TenantContext.requireTenantId(), executionId));
  }
}
