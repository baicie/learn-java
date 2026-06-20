package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.execution.dto.AgentSearchCasesRequest;
import io.aegisops.execution.dto.KnowledgeBaseSearchRequest;
import io.aegisops.execution.dto.KnowledgeBaseSearchResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AgentKnowledgeToolControllerTest {
  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void searchCasesUsesTenantContextInsteadOfRequestBodyTenantId() {
    TenantContext.setTenantId("tenant_real");

    FakeKnowledgeBaseSearchService searchService = new FakeKnowledgeBaseSearchService();
    AgentKnowledgeToolController controller = new AgentKnowledgeToolController(searchService);

    controller.searchCases(
        new AgentSearchCasesRequest("tenant_fake", "redis timeout", List.of("order-service"), 3));

    assertEquals("tenant_real", searchService.capturedTenantId);
    assertEquals("redis timeout", searchService.capturedRequest.query());
    assertEquals(List.of("incident_case"), searchService.capturedRequest.sourceTypes());
    assertEquals("agent", searchService.capturedRequest.createdBy());
  }

  private static class FakeKnowledgeBaseSearchService extends KnowledgeBaseSearchService {
    String capturedTenantId;
    KnowledgeBaseSearchRequest capturedRequest;

    FakeKnowledgeBaseSearchService() {
      super(null, null, null, new ObjectMapper());
    }

    @Override
    public KnowledgeBaseSearchResponse search(String tenantId, KnowledgeBaseSearchRequest request) {
      this.capturedTenantId = tenantId;
      this.capturedRequest = request;
      return new KnowledgeBaseSearchResponse(request.query(), request.topK(), List.of());
    }
  }
}
