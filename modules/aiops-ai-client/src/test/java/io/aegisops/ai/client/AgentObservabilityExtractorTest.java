package io.aegisops.ai.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentObservabilityExtractorTest {
  @Test
  void extractsRunStepsAndEvalChecks() {
    OffsetDateTime now = OffsetDateTime.now();
    Map<String, Object> raw = new HashMap<>();
    raw.put(
        "agentRun", makeAgentRun("run_1", "trace_1", List.of(makeStep("step_1", "load_context"))));
    raw.put("agentEval", makeAgentEval(List.of(makeCheck("required_fields"))));

    AgentDiagnosisResponse response =
        new AgentDiagnosisResponse(
            "agent-diagnosis.v1",
            "inc_1",
            "completed",
            "openai-compatible",
            "test-model",
            "aegisops_diagnosis_graph",
            "summary",
            "root",
            "impact",
            List.of("step"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            raw,
            now);

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
    OffsetDateTime now = OffsetDateTime.now();
    AgentDiagnosisResponse response =
        new AgentDiagnosisResponse(
            "agent-diagnosis.v1",
            "inc_1",
            "completed",
            "aiops-agent",
            "model",
            "agent",
            "summary",
            "root",
            "impact",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Map.of(),
            now);

    AgentObservabilityExtractor extractor = new AgentObservabilityExtractor(new ObjectMapper());
    var data = extractor.extract("diag_1", "tenant_1", "inc_1", "trace_1", response);

    assertTrue(data.run().isEmpty());
    assertTrue(data.steps().isEmpty());
    assertTrue(data.evalResults().isEmpty());
  }

  @Test
  void toleratesMalformedTimeAndNumericFields() {
    OffsetDateTime now = OffsetDateTime.now();
    Map<String, Object> raw = new HashMap<>();
    raw.put(
        "agentRun",
        Map.of(
            "runId", "run_1",
            "traceId", "trace_1",
            "startedAt", "bad-time",
            "finishedAt", "bad-time",
            "durationMs", "not-number",
            "steps",
                List.of(
                    Map.of(
                        "id", "step_1",
                        "sequenceNo", "not-number",
                        "stepName", "load_context",
                        "durationMs", "bad",
                        "startedAt", "bad-time"))));
    raw.put(
        "agentEval",
        Map.of(
            "evaluatorName",
            "aegisops-basic-eval-v1",
            "checks",
            List.of(
                Map.of(
                    "name", "required_fields",
                    "passed", true,
                    "score", "bad-score"))));

    AgentDiagnosisResponse response =
        new AgentDiagnosisResponse(
            "agent-diagnosis.v1",
            "inc_1",
            "completed",
            "openai-compatible",
            "test-model",
            "aegisops_diagnosis_graph",
            "summary",
            "root",
            "impact",
            List.of("step"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            raw,
            now);

    AgentObservabilityExtractor extractor = new AgentObservabilityExtractor(new ObjectMapper());
    var data = extractor.extract("diag_1", "tenant_1", "inc_1", "trace_1", response);

    assertTrue(data.run().isPresent());
    assertNull(data.run().get().startedAt());
    assertNull(data.run().get().finishedAt());
    assertEquals(0L, data.run().get().durationMs());

    assertEquals(1, data.steps().size());
    assertEquals(0, data.steps().get(0).sequenceNo());
    assertNull(data.steps().get(0).startedAt());
    assertEquals(0L, data.steps().get(0).durationMs());

    assertEquals(1, data.evalResults().size());
    assertEquals(0, data.evalResults().get(0).score().intValue());
  }

  private Map<String, Object> makeAgentRun(
      String runId, String traceId, List<Map<String, Object>> steps) {
    Map<String, Object> run = new HashMap<>();
    run.put("runId", runId);
    run.put("traceId", traceId);
    run.put("contractVersion", "agent-diagnosis.v1");
    run.put("generationMode", "openai-compatible");
    run.put("provider", "openai-compatible");
    run.put("model", "test-model");
    run.put("status", "completed");
    run.put("startedAt", "2026-06-16T10:00:00Z");
    run.put("finishedAt", "2026-06-16T10:00:01Z");
    run.put("durationMs", 1000);
    run.put("fallbackReason", "");
    run.put("safety", Map.of("autoExecutionAllowed", false));
    run.put("steps", steps);
    return run;
  }

  private Map<String, Object> makeStep(String id, String name) {
    Map<String, Object> step = new HashMap<>();
    step.put("id", id);
    step.put("sequenceNo", 1);
    step.put("stepName", name);
    step.put("stepType", "node");
    step.put("status", "completed");
    step.put("startedAt", "2026-06-16T10:00:00Z");
    step.put("finishedAt", "2026-06-16T10:00:00Z");
    step.put("durationMs", 1);
    step.put("inputSummary", "in");
    step.put("outputSummary", "out");
    step.put("metadata", Map.of());
    return step;
  }

  private Map<String, Object> makeAgentEval(List<Map<String, Object>> checks) {
    Map<String, Object> eval = new HashMap<>();
    eval.put("evaluatorName", "aegisops-basic-eval-v1");
    eval.put("score", 100);
    eval.put("passed", true);
    eval.put("checks", checks);
    return eval;
  }

  private Map<String, Object> makeCheck(String name) {
    Map<String, Object> check = new HashMap<>();
    check.put("name", name);
    check.put("passed", true);
    check.put("score", 100);
    check.put("reason", "");
    check.put("details", Map.of());
    return check;
  }
}
