package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.execution.dto.AgentEvalCaseCreateRequest;
import io.aegisops.execution.dto.AgentEvalCaseFromIncidentCaseRequest;
import io.aegisops.execution.dto.AgentEvalCaseResponse;
import io.aegisops.execution.dto.AgentEvalCaseResultResponse;
import io.aegisops.execution.dto.AgentEvalDatasetCreateRequest;
import io.aegisops.execution.dto.AgentEvalDatasetResponse;
import io.aegisops.execution.dto.AgentEvalRunCreateRequest;
import io.aegisops.execution.dto.AgentEvalRunResponse;
import io.aegisops.execution.dto.AgentPromptProfileCreateRequest;
import io.aegisops.execution.dto.AgentPromptProfileResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AgentEvalController {
  private final AgentEvalService service;

  public AgentEvalController(AgentEvalService service) {
    this.service = service;
  }

  @PostMapping("/api/agent-eval/datasets")
  public ApiResponse<AgentEvalDatasetResponse> createDataset(
      @RequestBody AgentEvalDatasetCreateRequest request) {
    return ApiResponse.ok(service.createDataset(TenantContext.requireTenantId(), request));
  }

  @GetMapping("/api/agent-eval/datasets")
  public ApiResponse<List<AgentEvalDatasetResponse>> listDatasets(
      @RequestParam(required = false) String status) {
    return ApiResponse.ok(service.listDatasets(TenantContext.requireTenantId(), status));
  }

  @GetMapping("/api/agent-eval/datasets/{datasetId}")
  public ApiResponse<AgentEvalDatasetResponse> getDataset(@PathVariable String datasetId) {
    return ApiResponse.ok(service.getDataset(TenantContext.requireTenantId(), datasetId));
  }

  @PostMapping("/api/agent-eval/datasets/{datasetId}/activate")
  public ApiResponse<AgentEvalDatasetResponse> activateDataset(@PathVariable String datasetId) {
    return ApiResponse.ok(service.activateDataset(TenantContext.requireTenantId(), datasetId));
  }

  @PostMapping("/api/agent-eval/datasets/{datasetId}/archive")
  public ApiResponse<AgentEvalDatasetResponse> archiveDataset(@PathVariable String datasetId) {
    return ApiResponse.ok(service.archiveDataset(TenantContext.requireTenantId(), datasetId));
  }

  @PostMapping("/api/agent-eval/datasets/{datasetId}/cases")
  public ApiResponse<AgentEvalCaseResponse> createCase(
      @PathVariable String datasetId, @RequestBody AgentEvalCaseCreateRequest request) {
    return ApiResponse.ok(service.createCase(TenantContext.requireTenantId(), datasetId, request));
  }

  @PostMapping("/api/agent-eval/datasets/{datasetId}/cases/from-incident-case/{caseId}")
  public ApiResponse<AgentEvalCaseResponse> createCaseFromIncidentCase(
      @PathVariable String datasetId,
      @PathVariable String caseId,
      @RequestBody(required = false) AgentEvalCaseFromIncidentCaseRequest request) {
    return ApiResponse.ok(
        service.createCaseFromIncidentCase(
            TenantContext.requireTenantId(), datasetId, caseId, request));
  }

  @GetMapping("/api/agent-eval/datasets/{datasetId}/cases")
  public ApiResponse<List<AgentEvalCaseResponse>> listCases(@PathVariable String datasetId) {
    return ApiResponse.ok(service.listCases(TenantContext.requireTenantId(), datasetId));
  }

  @PostMapping("/api/agent-eval/prompt-profiles")
  public ApiResponse<AgentPromptProfileResponse> createPromptProfile(
      @RequestBody AgentPromptProfileCreateRequest request) {
    return ApiResponse.ok(service.createPromptProfile(TenantContext.requireTenantId(), request));
  }

  @GetMapping("/api/agent-eval/prompt-profiles")
  public ApiResponse<List<AgentPromptProfileResponse>> listPromptProfiles(
      @RequestParam(required = false) String status) {
    return ApiResponse.ok(service.listPromptProfiles(TenantContext.requireTenantId(), status));
  }

  @GetMapping("/api/agent-eval/prompt-profiles/{profileId}")
  public ApiResponse<AgentPromptProfileResponse> getPromptProfile(@PathVariable String profileId) {
    return ApiResponse.ok(service.getPromptProfile(TenantContext.requireTenantId(), profileId));
  }

  @PostMapping("/api/agent-eval/prompt-profiles/{profileId}/archive")
  public ApiResponse<AgentPromptProfileResponse> archivePromptProfile(
      @PathVariable String profileId) {
    return ApiResponse.ok(service.archivePromptProfile(TenantContext.requireTenantId(), profileId));
  }

  @PostMapping("/api/agent-eval/runs")
  public ApiResponse<AgentEvalRunResponse> runEval(@RequestBody AgentEvalRunCreateRequest request) {
    return ApiResponse.ok(service.runEval(TenantContext.requireTenantId(), request));
  }

  @GetMapping("/api/agent-eval/runs/{runId}")
  public ApiResponse<AgentEvalRunResponse> getRun(@PathVariable String runId) {
    return ApiResponse.ok(service.getRun(TenantContext.requireTenantId(), runId));
  }

  @GetMapping("/api/agent-eval/runs/{runId}/results")
  public ApiResponse<List<AgentEvalCaseResultResponse>> listRunResults(@PathVariable String runId) {
    return ApiResponse.ok(service.listRunResults(TenantContext.requireTenantId(), runId));
  }
}
