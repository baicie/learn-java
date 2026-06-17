package io.aegisops.ai.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import io.aegisops.ai.client.dto.AiDiagnoseRequest;
import io.aegisops.ai.client.dto.AiDiagnosisRecord;
import io.aegisops.ai.client.dto.AiIncidentRecord;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AiDiagnosisServiceObservabilityFailureTest {
  @Test
  void diagnoseStillSucceedsWhenAgentRunPersistFails() {
    AiRepository repository = Mockito.mock(AiRepository.class);
    AiAgentClient agentClient = Mockito.mock(AiAgentClient.class);

    OffsetDateTime now = OffsetDateTime.parse("2026-06-16T10:00:00+09:00");

    when(repository.findIncident("tenant_1", "inc_1"))
        .thenReturn(Optional.of(
            new AiIncidentRecord(
                "inc_1",
                "tenant_1",
                "CPU high",
                "summary",
                "critical",
                "open",
                "zabbix",
                "asset_1",
                "agg",
                1,
                null,
                BigDecimal.ZERO,
                now.minusMinutes(10),
                now.minusMinutes(10),
                now,
                now.minusMinutes(10),
                now)));

    when(repository.listIncidentAlerts("tenant_1", "inc_1")).thenReturn(List.of());
    when(repository.findLatestRca("tenant_1", "inc_1")).thenReturn(Optional.empty());

    when(agentClient.diagnose(any()))
        .thenReturn(new AgentDiagnosisResponse(
            "agent-diagnosis.v1",
            "aiops-agent",
            "langgraph-deterministic",
            "aegisops_diagnosis_graph",
            "summary",
            "root",
            "impact",
            List.of("step"),
            List.of(),
            List.of(),
            rawWithAgentRunAndEval()));

    doThrow(new RuntimeException("agent_run insert failed"))
        .when(repository)
        .saveAgentRun(any());

    when(repository.findDiagnosis(Mockito.eq("tenant_1"), Mockito.anyString()))
        .thenReturn(Optional.of(
            new AiDiagnosisRecord(
                "diag_1",
                "tenant_1",
                "inc_1",
                "completed",
                "aiops-agent",
                "langgraph-deterministic",
                "aegisops_diagnosis_graph",
                "summary",
                "root",
                "impact",
                "[\"step\"]",
                "[]",
                "[]",
                now)));

    AiDiagnosisService service =
        new AiDiagnosisService(repository, agentClient, objectMapper());

    var response = service.diagnose("tenant_1", "inc_1", new AiDiagnoseRequest(true, "zh-CN"));

    assertEquals("summary", response.summary());

    verify(repository).saveDiagnosis(any());
    verify(repository).saveAgentRun(any());
    verify(repository).addIncidentTimeline(any());
  }

  private Map<String, Object> rawWithAgentRunAndEval() {
    Map<String, Object> agentRun = new HashMap<>();
    agentRun.put("runId", "run_1");
    agentRun.put("traceId", "trace_1");
    agentRun.put("contractVersion", "agent-diagnosis.v1");
    agentRun.put("generationMode", "deterministic");
    agentRun.put("provider", "aiops-agent");
    agentRun.put("model", "langgraph-deterministic");
    agentRun.put("status", "completed");
    agentRun.put("startedAt", "2026-06-16T10:00:00Z");
    agentRun.put("finishedAt", "2026-06-16T10:00:01Z");
    agentRun.put("durationMs", 1000);
    agentRun.put("safety", Map.of("autoExecutionAllowed", false));
    agentRun.put("steps", List.of());

    Map<String, Object> agentEval = new HashMap<>();
    agentEval.put("evaluatorName", "aegisops-basic-eval-v1");
    agentEval.put("score", 100);
    agentEval.put("passed", true);
    agentEval.put("checks", List.of());

    Map<String, Object> raw = new HashMap<>();
    raw.put("agentRun", agentRun);
    raw.put("agentEval", agentEval);
    return raw;
  }

  private ObjectMapper objectMapper() {
    return new ObjectMapper().registerModule(new JavaTimeModule());
  }
}
