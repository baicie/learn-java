package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.aegisops.common.security.DiagnosisGrantClaims;
import io.aegisops.execution.dto.AgentMemoryCreateRequest;
import io.aegisops.execution.dto.AgentMemorySearchRequest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class InternalAgentMemoryControllerTest {
  @Test
  void createRejectsTenantOutsideDiagnosisGrant() {
    AgentMemoryService service = mock(AgentMemoryService.class);
    InternalAgentMemoryController controller = new InternalAgentMemoryController(service);
    AgentMemoryCreateRequest request =
        new AgentMemoryCreateRequest(
            "tenant_forged",
            "incident",
            "inc_1",
            "incident_summary",
            "diagnosis",
            "diag_1",
            "title",
            "content",
            List.of(),
            0.8,
            300,
            "agent");

    assertThrows(SecurityException.class, () -> controller.create(request, grant()));
    verifyNoInteractions(service);
  }

  @Test
  void createRejectsIncidentScopeOutsideDiagnosisGrant() {
    AgentMemoryService service = mock(AgentMemoryService.class);
    InternalAgentMemoryController controller = new InternalAgentMemoryController(service);
    AgentMemoryCreateRequest request =
        new AgentMemoryCreateRequest(
            "tenant_grant",
            "incident",
            "inc_forged",
            "incident_summary",
            "diagnosis",
            "diag_1",
            "title",
            "content",
            List.of(),
            0.8,
            300,
            "agent");

    assertThrows(SecurityException.class, () -> controller.create(request, grant()));
    verifyNoInteractions(service);
  }

  @Test
  void searchRejectsTenantOutsideDiagnosisGrant() {
    AgentMemoryService service = mock(AgentMemoryService.class);
    InternalAgentMemoryController controller = new InternalAgentMemoryController(service);
    AgentMemorySearchRequest request =
        new AgentMemorySearchRequest(
            "tenant_forged", "timeout", "tenant", null, List.of(), List.of(), 5, "agent");

    assertThrows(SecurityException.class, () -> controller.search(request, grant()));
    verifyNoInteractions(service);
  }

  @Test
  void searchRejectsIncidentScopeOutsideDiagnosisGrant() {
    AgentMemoryService service = mock(AgentMemoryService.class);
    InternalAgentMemoryController controller = new InternalAgentMemoryController(service);
    AgentMemorySearchRequest request =
        new AgentMemorySearchRequest(
            "tenant_grant", "timeout", "incident", "inc_forged", List.of(), List.of(), 5, "agent");

    assertThrows(SecurityException.class, () -> controller.search(request, grant()));
    verifyNoInteractions(service);
  }

  private DiagnosisGrantClaims grant() {
    return new DiagnosisGrantClaims(
        "aiops-server",
        "aegisops-internal-api",
        "tenant_grant",
        "inc_1",
        "trace_1",
        Instant.parse("2026-07-30T08:00:00Z"),
        Instant.parse("2026-07-30T08:05:00Z"));
  }
}
