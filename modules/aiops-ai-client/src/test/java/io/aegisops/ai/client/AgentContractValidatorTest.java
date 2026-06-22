package io.aegisops.ai.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.ai.client.dto.AgentAlertContext;
import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import io.aegisops.ai.client.dto.AgentIncidentContext;
import io.aegisops.ai.client.dto.AgentRcaContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentContractValidatorTest {
  private final AgentContractValidator validator = new AgentContractValidator();

  @Test
  void validatesValidRequest() {
    assertDoesNotThrow(() -> validator.validateRequest(validRequest()));
  }

  @Test
  void rejectsInvalidContractVersionInRequest() {
    AgentDiagnosisRequest request =
        new AgentDiagnosisRequest(
            "bad",
            "tenant_1",
            "inc_1",
            new AgentIncidentContext(
                "inc_1", null, null, null, null, null, null, null, 0, null, null, null, null, null),
            List.of(),
            null,
            List.of(),
            List.of(),
            "zh-CN",
            "trace_1");

    AgentContractViolationException ex =
        assertThrows(
            AgentContractViolationException.class, () -> validator.validateRequest(request));

    assertEquals("AI_AGENT_CONTRACT_VIOLATION", ex.errorCode());
  }

  @Test
  void rejectsMissingIncidentInRequest() {
    AgentDiagnosisRequest request =
        new AgentDiagnosisRequest(
            AgentContract.DIAGNOSIS_CONTRACT_VERSION,
            "tenant_1",
            "inc_1",
            null,
            List.of(),
            null,
            List.of(),
            List.of(),
            "zh-CN",
            "trace_1");

    assertThrows(AgentContractViolationException.class, () -> validator.validateRequest(request));
  }

  @Test
  void validatesValidResponse() {
    assertDoesNotThrow(() -> validator.validateResponse(validResponse()));
  }

  @Test
  void rejectsUnsafeResponse() {
    AgentDiagnosisResponse response =
        new AgentDiagnosisResponse(
            AgentContract.DIAGNOSIS_CONTRACT_VERSION,
            "aiops-agent",
            "model",
            "agent",
            "summary",
            "root",
            "impact",
            List.of("Run rm -rf / automatically"),
            List.of(),
            List.of(),
            Map.of());

    AgentContractViolationException ex =
        assertThrows(
            AgentContractViolationException.class, () -> validator.validateResponse(response));

    assertTrue(ex.getMessage().contains("forbidden unsafe action keyword"));
  }

  private AgentDiagnosisRequest validRequest() {
    return new AgentDiagnosisRequest(
        AgentContract.DIAGNOSIS_CONTRACT_VERSION,
        "tenant_1",
        "inc_1",
        new AgentIncidentContext(
            "inc_1", null, null, null, null, null, null, null, 0, null, null, null, null, null),
        List.of(
            new AgentAlertContext(
                "alert_1", null, null, null, null, null, null, null, null, null, null, null)),
        null,
        List.of(),
        List.of(),
        "zh-CN",
        "trace_1");
  }

  private AgentDiagnosisResponse validResponse() {
    return new AgentDiagnosisResponse(
        AgentContract.DIAGNOSIS_CONTRACT_VERSION,
        "aiops-agent",
        "langgraph-deterministic",
        "aegisops_diagnosis_graph",
        "summary",
        "root",
        "impact",
        List.of("step"),
        List.of("runbook"),
        List.of("risk"),
        Map.of("ok", true));
  }

  @SuppressWarnings("unused")
  private static final class _unused {
    // Force compile of AgentRcaContext to avoid unused import warning if filtered out.
    AgentRcaContext rca = new AgentRcaContext("r", null, null, null, null, null, null, null, null);
  }
}
