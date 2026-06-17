package io.aegisops.ai.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import io.aegisops.ai.client.dto.AgentEvalResultCommand;
import io.aegisops.ai.client.dto.AgentRunStepCommand;
import io.aegisops.ai.client.dto.SaveAgentRunCommand;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class AgentObservabilityExtractor {
  private final ObjectMapper objectMapper;

  public AgentObservabilityExtractor(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public AgentObservabilityData extract(
      String diagnosisId,
      String tenantId,
      String incidentId,
      String traceId,
      AgentDiagnosisResponse response) {
    Map<String, Object> raw = response.raw() == null ? Map.of() : response.raw();

    Map<String, Object> agentRun = asMap(raw.get("agentRun"));
    Map<String, Object> agentEval = asMap(raw.get("agentEval"));

    if (agentRun.isEmpty()) {
      return new AgentObservabilityData(Optional.empty(), List.of(), List.of());
    }

    String runId = string(agentRun.get("runId"), newId("run"));

    SaveAgentRunCommand run =
        new SaveAgentRunCommand(
            runId,
            diagnosisId,
            tenantId,
            incidentId,
            string(agentRun.get("traceId"), traceId),
            string(agentRun.get("contractVersion"), response.contractVersion()),
            string(agentRun.get("generationMode"), string(raw.get("generationMode"), "deterministic")),
            string(agentRun.get("provider"), response.provider()),
            string(agentRun.get("model"), response.model()),
            string(agentRun.get("status"), "completed"),
            offsetOrNull(agentRun.get("startedAt")),
            offsetOrNull(agentRun.get("finishedAt")),
            longValue(agentRun.get("durationMs"), 0L),
            string(agentRun.get("fallbackReason"), string(raw.get("fallbackReason"), "")),
            writeJson(asMap(agentRun.get("safety"))),
            writeJson(agentEval));

    List<AgentRunStepCommand> steps =
        asList(agentRun.get("steps")).stream()
            .map(item -> toStep(runId, asMap(item)))
            .toList();

    List<AgentEvalResultCommand> evalResults =
        asList(agentEval.get("checks")).stream()
            .map(item -> toEval(runId, string(agentEval.get("evaluatorName"), "aegisops-basic-eval-v1"), asMap(item)))
            .toList();

    return new AgentObservabilityData(Optional.of(run), steps, evalResults);
  }

  private AgentRunStepCommand toStep(String runId, Map<String, Object> step) {
    return new AgentRunStepCommand(
        string(step.get("id"), newId("step")),
        runId,
        intValue(step.get("sequenceNo"), 0),
        string(step.get("stepName"), "unknown"),
        string(step.get("stepType"), "node"),
        string(step.get("status"), "completed"),
        offsetOrNull(step.get("startedAt")),
        offsetOrNull(step.get("finishedAt")),
        longValue(step.get("durationMs"), 0L),
        string(step.get("inputSummary"), ""),
        string(step.get("outputSummary"), ""),
        string(step.get("errorMessage"), ""),
        writeJson(asMap(step.get("metadata"))));
  }

  private AgentEvalResultCommand toEval(String runId, String evaluatorName, Map<String, Object> check) {
    return new AgentEvalResultCommand(
        newId("eval"),
        runId,
        evaluatorName,
        string(check.get("name"), "unknown"),
        bool(check.get("passed"), false),
        decimal(check.get("score"), BigDecimal.ZERO),
        string(check.get("reason"), ""),
        writeJson(asMap(check.get("details"))));
  }

  public record AgentObservabilityData(
      Optional<SaveAgentRunCommand> run,
      List<AgentRunStepCommand> steps,
      List<AgentEvalResultCommand> evalResults) {}

  private Map<String, Object> asMap(Object value) {
    if (value instanceof Map<?, ?> map) {
      return objectMapper.convertValue(map, new TypeReference<Map<String, Object>>() {});
    }
    return Map.of();
  }

  private List<Object> asList(Object value) {
    if (value instanceof List<?> list) {
      return List.copyOf(list);
    }
    return List.of();
  }

  private String string(Object value, String fallback) {
    if (value == null) {
      return fallback;
    }

    String text = String.valueOf(value);
    return text.isBlank() ? fallback : text;
  }

  private OffsetDateTime offsetOrNull(Object value) {
    if (value == null || String.valueOf(value).isBlank()) {
      return null;
    }

    try {
      return OffsetDateTime.parse(String.valueOf(value).replace("Z", "+00:00"));
    } catch (DateTimeParseException ex) {
      return null;
    }
  }

  private int intValue(Object value, int fallback) {
    if (value instanceof Number number) {
      return number.intValue();
    }

    if (value == null || String.valueOf(value).isBlank()) {
      return fallback;
    }

    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private long longValue(Object value, long fallback) {
    if (value instanceof Number number) {
      return number.longValue();
    }

    if (value == null || String.valueOf(value).isBlank()) {
      return fallback;
    }

    try {
      return Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private boolean bool(Object value, boolean fallback) {
    if (value instanceof Boolean b) {
      return b;
    }

    if (value == null || String.valueOf(value).isBlank()) {
      return fallback;
    }

    return Boolean.parseBoolean(String.valueOf(value));
  }

  private BigDecimal decimal(Object value, BigDecimal fallback) {
    if (value instanceof BigDecimal decimal) {
      return decimal;
    }

    if (value instanceof Number number) {
      return BigDecimal.valueOf(number.doubleValue());
    }

    if (value == null || String.valueOf(value).isBlank()) {
      return fallback;
    }

    try {
      return new BigDecimal(String.valueOf(value));
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (Exception ex) {
      return "{}";
    }
  }

  private static String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
