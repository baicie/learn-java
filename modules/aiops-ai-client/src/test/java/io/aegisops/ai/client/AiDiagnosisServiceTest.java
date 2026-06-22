package io.aegisops.ai.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import io.aegisops.ai.client.dto.AiAlertRecord;
import io.aegisops.ai.client.dto.AiDiagnoseRequest;
import io.aegisops.ai.client.dto.AiDiagnosisRecord;
import io.aegisops.ai.client.dto.AiDiagnosisResponse;
import io.aegisops.ai.client.dto.AiEvidenceRecord;
import io.aegisops.ai.client.dto.AiIncidentRecord;
import io.aegisops.ai.client.dto.AiRcaRecord;
import io.aegisops.ai.client.dto.AiTimelineRecord;
import io.aegisops.ai.client.dto.SaveDiagnosisCommand;
import io.aegisops.ai.client.dto.TimelineCommand;
import io.aegisops.common.exception.AppException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiDiagnosisServiceTest {
  @Test
  void recomputesAndSavesContractedDiagnosis() {
    FakeAiRepository repository = new FakeAiRepository();
    repository.incident = incident();
    repository.alerts = List.of(alert());
    repository.rca = rca();

    AiDiagnosisService service =
        new AiDiagnosisService(repository, new FakeAgentClient(), objectMapper());

    AiDiagnosisResponse response =
        service.diagnose("tenant_1", "inc_1", new AiDiagnoseRequest(true, "zh-CN"));

    assertEquals(1, repository.savedCount);
    assertEquals(1, repository.timelineCount);
    assertEquals("aegisops_diagnosis_graph", response.agentName());
    assertTrue(repository.lastRequestJson.contains("agent-diagnosis.v1"));
  }

  @Test
  void returnsFreshLatestWhenForceDisabled() {
    FakeAiRepository repository = new FakeAiRepository();
    repository.incident = incident();
    repository.latest = diagnosis(repository.incident.lastSeenAt().plusSeconds(1));

    AiDiagnosisService service =
        new AiDiagnosisService(repository, new FakeAgentClient(), objectMapper());

    AiDiagnosisResponse response =
        service.diagnose("tenant_1", "inc_1", new AiDiagnoseRequest(false, "zh-CN"));

    assertEquals("diag_cached", response.id());
    assertEquals(0, repository.savedCount);
    assertEquals(0, repository.timelineCount);
  }

  @Test
  void rejectsUnsafeAgentResponse() {
    FakeAiRepository repository = new FakeAiRepository();
    repository.incident = incident();

    AiDiagnosisService service =
        new AiDiagnosisService(repository, new UnsafeAgentClient(), objectMapper());

    AppException ex =
        assertThrows(
            AppException.class,
            () -> service.diagnose("tenant_1", "inc_1", new AiDiagnoseRequest(true, "zh-CN")));

    assertEquals("AI_AGENT_CONTRACT_VIOLATION", ex.errorCode());
  }

  private ObjectMapper objectMapper() {
    return new ObjectMapper().registerModule(new JavaTimeModule());
  }

  private AiIncidentRecord incident() {
    OffsetDateTime now = OffsetDateTime.parse("2026-06-14T10:00:00+09:00");
    return new AiIncidentRecord(
        "inc_1",
        "tenant_1",
        "CPU high",
        "summary",
        "critical",
        "open",
        "system",
        "asset_1",
        "zabbix:fp_cpu",
        2,
        "CPU saturation",
        new BigDecimal("0.8000"),
        now.minusMinutes(5),
        now,
        now,
        now,
        now);
  }

  private AiAlertRecord alert() {
    OffsetDateTime now = OffsetDateTime.parse("2026-06-14T10:00:00+09:00");
    return new AiAlertRecord(
        "alert_1",
        "zabbix",
        "event_1",
        "critical",
        "CPU high",
        "CPU is high",
        "asset_1",
        "host",
        "host-1",
        "fp_cpu",
        "{}",
        now);
  }

  private AiRcaRecord rca() {
    OffsetDateTime now = OffsetDateTime.parse("2026-06-14T10:00:00+09:00");
    return new AiRcaRecord(
        "rca_1", "CPU saturation", new BigDecimal("0.8000"), "RCA summary", "[]", "rules-v1", now);
  }

  private AiDiagnosisRecord diagnosis(OffsetDateTime createdAt) {
    return new AiDiagnosisRecord(
        "diag_cached",
        "tenant_1",
        "inc_1",
        "completed",
        "aiops-agent",
        "langgraph-deterministic",
        "aegisops_diagnosis_graph",
        "cached summary",
        "cached root",
        "cached impact",
        "[\"step\"]",
        "[]",
        "[]",
        "{}",
        createdAt);
  }

  private static final class FakeAgentClient implements AiAgentClient {
    @Override
    public AgentDiagnosisResponse diagnose(AgentDiagnosisRequest request) {
      assertEquals(AgentContract.DIAGNOSIS_CONTRACT_VERSION, request.contractVersion());
      assertNotNull(request.traceId());

      return new AgentDiagnosisResponse(
          AgentContract.DIAGNOSIS_CONTRACT_VERSION,
          "inc_1",
          "completed",
          "aiops-agent",
          "langgraph-deterministic",
          "aegisops_diagnosis_graph",
          "AI summary",
          "CPU saturation",
          "Service latency may increase.",
          List.of("Check CPU usage", "Check top process"),
          List.of("Host resource saturation runbook"),
          List.of("Do not restart blindly"),
          List.of(),
          List.of(),
          List.of(),
          Map.of("contractVersion", request.contractVersion()),
          OffsetDateTime.now());
    }
  }

  private static final class UnsafeAgentClient implements AiAgentClient {
    @Override
    public AgentDiagnosisResponse diagnose(AgentDiagnosisRequest request) {
      return new AgentDiagnosisResponse(
          AgentContract.DIAGNOSIS_CONTRACT_VERSION,
          "inc_1",
          "completed",
          "aiops-agent",
          "langgraph-deterministic",
          "aegisops_diagnosis_graph",
          "AI summary",
          "bad root",
          "bad impact",
          List.of("自动执行删除 namespace"),
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          Map.of(),
          OffsetDateTime.now());
    }
  }

  private static final class FakeAiRepository implements AiRepository {
    AiIncidentRecord incident;
    List<AiAlertRecord> alerts = List.of();
    AiRcaRecord rca;
    AiDiagnosisRecord latest;
    AiDiagnosisRecord saved;
    int savedCount;
    int timelineCount;
    String lastRequestJson;

    @Override
    public Optional<AiIncidentRecord> findIncident(String tenantId, String incidentId) {
      if (incident == null) {
        return Optional.empty();
      }
      if (!incident.tenantId().equals(tenantId) || !incident.id().equals(incidentId)) {
        return Optional.empty();
      }
      return Optional.of(incident);
    }

    @Override
    public List<AiAlertRecord> listIncidentAlerts(String tenantId, String incidentId) {
      return alerts;
    }

    @Override
    public Optional<AiRcaRecord> findLatestRca(String tenantId, String incidentId) {
      return Optional.ofNullable(rca);
    }

    @Override
    public Optional<AiDiagnosisRecord> findLatestDiagnosis(String tenantId, String incidentId) {
      return Optional.ofNullable(latest);
    }

    @Override
    public Optional<AiDiagnosisRecord> findDiagnosis(String tenantId, String diagnosisId) {
      if (saved == null || !saved.tenantId().equals(tenantId) || !saved.id().equals(diagnosisId)) {
        return Optional.empty();
      }
      return Optional.of(saved);
    }

    @Override
    public List<AiEvidenceRecord> listDiagnosisEvidence(String tenantId, String incidentId) {
      return List.of();
    }

    @Override
    public List<AiTimelineRecord> listIncidentTimeline(
        String tenantId, String incidentId, int limit) {
      return List.of();
    }

    @Override
    public void saveDiagnosis(SaveDiagnosisCommand command) {
      savedCount++;
      lastRequestJson = command.requestJson();
      saved =
          new AiDiagnosisRecord(
              command.id(),
              command.tenantId(),
              command.incidentId(),
              "completed",
              command.response().provider(),
              command.response().model(),
              command.response().agentName(),
              command.response().summary(),
              command.response().rootCause(),
              command.response().impact(),
              command.nextStepsJson(),
              command.runbookSuggestionsJson(),
              command.risksJson(),
              command.rawJson(),
              OffsetDateTime.parse("2026-06-14T10:00:00+09:00"));
    }

    @Override
    public void addIncidentTimeline(TimelineCommand command) {
      timelineCount++;
    }
  }
}
