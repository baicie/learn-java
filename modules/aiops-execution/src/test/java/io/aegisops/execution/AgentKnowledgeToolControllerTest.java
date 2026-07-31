package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.security.DiagnosisGrantClaims;
import io.aegisops.execution.dto.AgentSearchCasesRequest;
import io.aegisops.execution.dto.KnowledgeBaseSearchRequest;
import io.aegisops.execution.dto.KnowledgeBaseSearchResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentKnowledgeToolControllerTest {
  @Test
  void searchCasesUsesDiagnosisGrantTenant() {
    FakeKnowledgeBaseSearchService searchService = new FakeKnowledgeBaseSearchService();
    AgentKnowledgeToolController controller = new AgentKnowledgeToolController(searchService);

    controller.searchCases(
        new AgentSearchCasesRequest("tenant_real", "redis timeout", List.of("order-service"), 3),
        grant());

    assertEquals("tenant_real", searchService.capturedTenantId);
    assertEquals("redis timeout", searchService.capturedRequest.query());
    assertEquals(List.of("incident_case"), searchService.capturedRequest.sourceTypes());
    assertEquals("agent", searchService.capturedRequest.createdBy());
  }

  @Test
  void searchCasesRejectsTenantOutsideDiagnosisGrant() {
    AgentKnowledgeToolController controller =
        new AgentKnowledgeToolController(new FakeKnowledgeBaseSearchService());

    assertThrows(
        SecurityException.class,
        () ->
            controller.searchCases(
                new AgentSearchCasesRequest("tenant_fake", "redis timeout", List.of(), 3),
                grant()));
  }

  private DiagnosisGrantClaims grant() {
    return new DiagnosisGrantClaims(
        "aiops-server",
        "aegisops-internal-api",
        "tenant_real",
        "inc_1",
        "trace_1",
        Instant.parse("2026-07-30T08:00:00Z"),
        Instant.parse("2026-07-30T08:05:00Z"));
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
