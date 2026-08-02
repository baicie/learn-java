package io.aegisops.ai.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.ai.client.dto.AgentAlertContext;
import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import io.aegisops.ai.client.dto.AgentEvidenceContext;
import io.aegisops.ai.client.dto.AgentIncidentContext;
import io.aegisops.ai.client.dto.AgentTimelineContext;
import java.time.OffsetDateTime;
import java.util.Arrays;
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
            "trace_1",
            "diag_1");

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
            "trace_1",
            "diag_1");

    assertThrows(AgentContractViolationException.class, () -> validator.validateRequest(request));
  }

  @Test
  void rejectsNullEvidenceList() {
    AgentDiagnosisRequest request =
        new AgentDiagnosisRequest(
            AgentContract.DIAGNOSIS_CONTRACT_VERSION,
            "tenant_1",
            "inc_1",
            new AgentIncidentContext(
                "inc_1", null, null, null, null, null, null, null, 0, null, null, null, null, null),
            List.of(),
            null,
            null,
            List.of(),
            "zh-CN",
            "trace_1",
            "diag_1");

    AgentContractViolationException ex =
        assertThrows(
            AgentContractViolationException.class, () -> validator.validateRequest(request));

    assertEquals("AI_AGENT_CONTRACT_VIOLATION", ex.errorCode());
    assertTrue(ex.getMessage().contains("evidence is required"));
  }

  @Test
  void rejectsInvalidEvidenceItem() {
    AgentDiagnosisRequest request =
        new AgentDiagnosisRequest(
            AgentContract.DIAGNOSIS_CONTRACT_VERSION,
            "tenant_1",
            "inc_1",
            new AgentIncidentContext(
                "inc_1", null, null, null, null, null, null, null, 0, null, null, null, null, null),
            List.of(),
            null,
            List.of(
                new AgentEvidenceContext(
                    null, "", "zabbix", "", null, null, null, null, null, "{}")),
            List.of(),
            "zh-CN",
            "trace_1",
            "diag_1");

    AgentContractViolationException ex =
        assertThrows(
            AgentContractViolationException.class, () -> validator.validateRequest(request));

    assertEquals("AI_AGENT_CONTRACT_VIOLATION", ex.errorCode());
  }

  @Test
  void rejectsNullTimelineList() {
    AgentDiagnosisRequest request =
        new AgentDiagnosisRequest(
            AgentContract.DIAGNOSIS_CONTRACT_VERSION,
            "tenant_1",
            "inc_1",
            new AgentIncidentContext(
                "inc_1", null, null, null, null, null, null, null, 0, null, null, null, null, null),
            List.of(),
            null,
            List.of(),
            null,
            "zh-CN",
            "trace_1",
            "diag_1");

    AgentContractViolationException ex =
        assertThrows(
            AgentContractViolationException.class, () -> validator.validateRequest(request));

    assertEquals("AI_AGENT_CONTRACT_VIOLATION", ex.errorCode());
    assertTrue(ex.getMessage().contains("timeline is required"));
  }

  @Test
  void rejectsNullTimelineItem() {
    AgentDiagnosisRequest request =
        new AgentDiagnosisRequest(
            AgentContract.DIAGNOSIS_CONTRACT_VERSION,
            "tenant_1",
            "inc_1",
            new AgentIncidentContext(
                "inc_1", null, null, null, null, null, null, null, 0, null, null, null, null, null),
            List.of(),
            null,
            List.of(),
            Arrays.asList((AgentTimelineContext) null),
            "zh-CN",
            "trace_1",
            "diag_1");

    AgentContractViolationException ex =
        assertThrows(
            AgentContractViolationException.class, () -> validator.validateRequest(request));

    assertEquals("AI_AGENT_CONTRACT_VIOLATION", ex.errorCode());
    assertTrue(ex.getMessage().contains("timeline[0] is null"));
  }

  @Test
  void rejectsMissingDiagnosisId() {
    AgentDiagnosisRequest request = validRequest(null);

    AgentContractViolationException ex =
        assertThrows(
            AgentContractViolationException.class, () -> validator.validateRequest(request));

    assertTrue(ex.getMessage().contains("diagnosisId is required"));
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
            "inc_1",
            "completed",
            "aiops-agent",
            "model",
            "agent",
            "summary",
            "root",
            "impact",
            List.of("Run rm -rf / automatically"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Map.of(),
            OffsetDateTime.now());

    AgentContractViolationException ex =
        assertThrows(
            AgentContractViolationException.class, () -> validator.validateResponse(response));

    assertTrue(ex.getMessage().contains("forbidden unsafe action keyword"));
  }

  private AgentDiagnosisRequest validRequest() {
    return validRequest("diag_1");
  }

  private AgentDiagnosisRequest validRequest(String diagnosisId) {
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
        "trace_1",
        diagnosisId);
  }

  private AgentDiagnosisResponse validResponse() {
    return new AgentDiagnosisResponse(
        AgentContract.DIAGNOSIS_CONTRACT_VERSION,
        "inc_1",
        "completed",
        "aiops-agent",
        "langgraph-deterministic",
        "aegisops_diagnosis_graph",
        "summary",
        "root",
        "impact",
        List.of("step"),
        List.of("runbook"),
        List.of("risk"),
        List.of(),
        List.of(),
        List.of(),
        Map.of("ok", true),
        OffsetDateTime.now());
  }
}
