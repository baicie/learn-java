package io.aegisops.ai.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentObservabilityExtractorTest {
  @Test
  void extractsRunStepsAndEvalChecks() {
    Map<String, Object> step = new HashMap<>();
    step.put("id", "step_1");
    step.put("sequenceNo", 1);
    step.put("stepName", "load_context");
    step.put("stepType", "node");
    step.put("status", "completed");
    step.put("startedAt", "2026-06-16T10:00:00Z");
    step.put("finishedAt", "2026-06-16T10:00:00Z");
    step.put("durationMs", 1);
    step.put("inputSummary", "in");
    step.put("outputSummary", "out");
    step.put("metadata", Map.of());

    Map<String, Object> agentRun = new HashMap<>();
    agentRun.put("runId", "run_1");
    agentRun.put("traceId", "trace_1");
    agentRun.put("contractVersion", "agent-diagnosis.v1");
    agentRun.put("generationMode", "openai-compatible");
    agentRun.put("provider", "openai-compatible");
    agentRun.put("model", "test-model");
    agentRun.put("status", "completed");
    agentRun.put("startedAt", "2026-06-16T10:00:00Z");
    agentRun.put("finishedAt", "2026-06-16T10:00:01Z");
    agentRun.put("durationMs", 1000);
    agentRun.put("fallbackReason", "");
    agentRun.put("safety", Map.of("autoExecutionAllowed", false));
    agentRun.put("steps", List.of(step));

    Map<String, Object> check = new HashMap<>();
    check.put("name", "required_fields");
    check.put("passed", true);
    check.put("score", 100);
    check.put("reason", "");
    check.put("details", Map.of());

    Map<String, Object> agentEval = new HashMap<>();
    agentEval.put("evaluatorName", "aegisops-basic-eval-v1");
    agentEval.put("score", 100);
    agentEval.put("passed", true);
    agentEval.put("checks", List.of(check));

    Map<String, Object> raw = new HashMap<>();
    raw.put("agentRun", agentRun);
    raw.put("agentEval", agentEval);

    AgentDiagnosisResponse response =
        new AgentDiagnosisResponse(
            "agent-diagnosis.v1",
            "openai-compatible",
            "test-model",
            "aegisops_diagnosis_graph",
            "summary",
            "root",
            "impact",
            List.of("step"),
            List.of(),
            List.of(),
            raw);

    AgentObservabilityExtractor extractor = new AgentObservabilityExtractor(new ObjectMapper());
    var data = extractor.extract("diag_1", "tenant_1", "inc_1", "trace_1", response);

    assertTrue(data.run().isPresent());
    assertEquals("run_1", data.run().get().id());
    assertEquals(1, data.steps().size());
    assertEquals("load_context", data.steps().get(0).stepName());
    assertEquals(1, data.evalResults().size());
    assertEquals("required_fields", data.evalResults().get(0).checkName());
  }

  @Test
  void returnsEmptyWhenRawHasNoAgentRun() {
    AgentDiagnosisResponse response =
        new AgentDiagnosisResponse(
            "agent-diagnosis.v1",
            "aiops-agent",
            "model",
            "agent",
            "summary",
            "root",
            "impact",
            List.of(),
            List.of(),
            List.of(),
            Map.of());

    AgentObservabilityExtractor extractor = new AgentObservabilityExtractor(new ObjectMapper());
    var data = extractor.extract("diag_1", "tenant_1", "inc_1", "trace_1", response);

    assertTrue(data.run().isEmpty());
    assertTrue(data.steps().isEmpty());
    assertTrue(data.evalResults().isEmpty());
  }
}
