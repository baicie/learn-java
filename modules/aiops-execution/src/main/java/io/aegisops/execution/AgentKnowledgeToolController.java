package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.execution.dto.AgentSearchCasesRequest;
import io.aegisops.execution.dto.KnowledgeBaseSearchRequest;
import io.aegisops.execution.dto.KnowledgeBaseSearchResponse;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal read-only tool endpoint for Python Agent.
 *
 * <p>This endpoint does not trigger execution, rollback, runbook, or approval.
 */
@RestController
public class AgentKnowledgeToolController {
  private final KnowledgeBaseSearchService searchService;

  public AgentKnowledgeToolController(KnowledgeBaseSearchService searchService) {
    this.searchService = searchService;
  }

  @PostMapping("/internal/agent/tools/search-cases")
  public ApiResponse<KnowledgeBaseSearchResponse> searchCases(
      @RequestBody AgentSearchCasesRequest request) {
    return ApiResponse.ok(
        searchService.search(
            request.tenantId(),
            new KnowledgeBaseSearchRequest(
                request.query(),
                List.of("incident_case"),
                request.tags(),
                request.topK(),
                "agent")));
  }
}
