package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.exception.AppException;
import io.aegisops.common.security.DiagnosisGrantAuthorization;
import io.aegisops.common.security.DiagnosisGrantClaims;
import io.aegisops.execution.dto.AgentSearchCasesRequest;
import io.aegisops.execution.dto.KnowledgeBaseSearchRequest;
import io.aegisops.execution.dto.KnowledgeBaseSearchResponse;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal read-only tool endpoint for Python Agent.
 *
 * <p>This endpoint does not trigger execution, rollback, runbook, or approval.
 *
 * <p>Tenant boundary is bound to the verified Diagnosis Grant.
 */
@RestController
public class AgentKnowledgeToolController {
  private final KnowledgeBaseSearchService searchService;

  public AgentKnowledgeToolController(KnowledgeBaseSearchService searchService) {
    this.searchService = searchService;
  }

  @PostMapping("/internal/agent/tools/search-cases")
  public ApiResponse<KnowledgeBaseSearchResponse> searchCases(
      @RequestBody AgentSearchCasesRequest request,
      @RequestAttribute(DiagnosisGrantAuthorization.REQUEST_ATTRIBUTE)
          DiagnosisGrantClaims claims) {
    if (request == null) {
      throw new AppException(
          "AGENT_SEARCH_CASES_REQUEST_REQUIRED", "Search cases request is required");
    }

    String tenantId = DiagnosisGrantAuthorization.requireTenant(claims, request.tenantId());

    return ApiResponse.ok(
        searchService.search(
            tenantId,
            new KnowledgeBaseSearchRequest(
                request.query(),
                List.of("incident_case"),
                request.tags(),
                request.topK(),
                "agent")));
  }
}
