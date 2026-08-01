package io.aegisops.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aegisops.execution.dto.ExecutionRunCreateCommand;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionStepCreateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ExecutionSnapshotHasher {
  private final ObjectMapper objectMapper;

  public ExecutionSnapshotHasher(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String sha256(
      ExecutionRunCreateCommand run, List<ExecutionStepCreateCommand> executionSteps) {
    return sha256(canonicalJson(run, executionSteps));
  }

  public String sha256(ExecutionRunRecord run, List<ExecutionStepRecord> executionSteps) {
    return sha256(canonicalJson(run, executionSteps));
  }

  public String canonicalJson(
      ExecutionRunCreateCommand run, List<ExecutionStepCreateCommand> executionSteps) {
    ObjectNode root = runNode(run);
    ArrayNode steps = root.putArray("steps");
    executionSteps.stream()
        .sorted(
            Comparator.comparingInt(ExecutionStepCreateCommand::sequenceNo)
                .thenComparing(ExecutionStepCreateCommand::id))
        .map(this::stepNode)
        .forEach(steps::add);
    return write(root);
  }

  public String canonicalJson(ExecutionRunRecord run, List<ExecutionStepRecord> executionSteps) {
    ObjectNode root = runNode(run);
    ArrayNode steps = root.putArray("steps");
    executionSteps.stream()
        .sorted(
            Comparator.comparingInt(ExecutionStepRecord::sequenceNo)
                .thenComparing(ExecutionStepRecord::id))
        .map(this::stepNode)
        .forEach(steps::add);
    return write(root);
  }

  private ObjectNode runNode(ExecutionRunCreateCommand run) {
    ObjectNode node = objectMapper.createObjectNode();
    put(node, "executionId", run.id());
    put(node, "tenantId", run.tenantId());
    put(node, "incidentId", run.incidentId());
    put(node, "planId", run.planId());
    put(node, "mode", run.mode());
    put(node, "requestedBy", run.requestedBy());
    put(node, "executionKind", run.executionKind());
    put(node, "rollbackPlanId", run.rollbackPlanId());
    put(node, "rollbackOfExecutionId", run.rollbackOfExecutionId());
    put(node, "retryOfExecutionId", run.retryOfExecutionId());
    node.put("attempt", run.attempt());
    node.put("maxAttempts", run.maxAttempts());
    node.put("timeoutSeconds", run.timeoutSeconds());
    put(node, "approvalId", run.approvalId());
    node.set("approvalSnapshot", parseCanonicalJson(run.approvalSnapshotJson()));
    put(node, "planRiskLevel", run.planRiskLevel());
    return node;
  }

  private ObjectNode runNode(ExecutionRunRecord run) {
    ObjectNode node = objectMapper.createObjectNode();
    put(node, "executionId", run.id());
    put(node, "tenantId", run.tenantId());
    put(node, "incidentId", run.incidentId());
    put(node, "planId", run.planId());
    put(node, "mode", run.mode());
    put(node, "requestedBy", run.requestedBy());
    put(node, "executionKind", run.executionKind());
    put(node, "rollbackPlanId", run.rollbackPlanId());
    put(node, "rollbackOfExecutionId", run.rollbackOfExecutionId());
    put(node, "retryOfExecutionId", run.retryOfExecutionId());
    node.put("attempt", run.attempt());
    node.put("maxAttempts", run.maxAttempts());
    node.put("timeoutSeconds", run.timeoutSeconds());
    put(node, "approvalId", run.approvalId());
    node.set("approvalSnapshot", parseCanonicalJson(run.approvalSnapshotJson()));
    put(node, "planRiskLevel", run.planRiskLevel());
    return node;
  }

  private ObjectNode stepNode(ExecutionStepCreateCommand step) {
    ObjectNode node = objectMapper.createObjectNode();
    put(node, "id", step.id());
    put(node, "planStepId", step.planStepId());
    node.put("sequence", step.sequenceNo());
    put(node, "name", step.name());
    put(node, "actionType", step.actionType());
    put(node, "targetType", step.targetType());
    node.set("actionPayload", parseCanonicalJson(step.actionPayloadJson()));
    put(node, "commandSnapshot", step.commandSnapshot());
    node.put("attempt", step.attempt());
    node.put("timeoutSeconds", step.timeoutSeconds());
    return node;
  }

  private ObjectNode stepNode(ExecutionStepRecord step) {
    ObjectNode node = objectMapper.createObjectNode();
    put(node, "id", step.id());
    put(node, "planStepId", step.planStepId());
    node.put("sequence", step.sequenceNo());
    put(node, "name", step.name());
    put(node, "actionType", step.actionType());
    put(node, "targetType", step.targetType());
    node.set("actionPayload", parseCanonicalJson(step.actionPayloadJson()));
    put(node, "commandSnapshot", step.commandSnapshot());
    node.put("attempt", step.attempt());
    node.put("timeoutSeconds", step.timeoutSeconds());
    return node;
  }

  private JsonNode parseCanonicalJson(String value) {
    if (value == null || value.isBlank()) {
      return objectMapper.nullNode();
    }
    try {
      return canonicalize(objectMapper.readTree(value));
    } catch (Exception exception) {
      throw new IllegalArgumentException("Execution snapshot contains invalid JSON", exception);
    }
  }

  private JsonNode canonicalize(JsonNode node) {
    if (node.isObject()) {
      ObjectNode sorted = objectMapper.createObjectNode();
      node.propertyStream()
          .sorted(java.util.Map.Entry.comparingByKey())
          .forEach(entry -> sorted.set(entry.getKey(), canonicalize(entry.getValue())));
      return sorted;
    }
    if (node.isArray()) {
      ArrayNode values = objectMapper.createArrayNode();
      node.forEach(value -> values.add(canonicalize(value)));
      return values;
    }
    return node;
  }

  private String write(JsonNode value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to serialize execution snapshot", exception);
    }
  }

  private String sha256(String canonicalJson) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(digest).toLowerCase(Locale.ROOT);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to hash execution snapshot", exception);
    }
  }

  private static void put(ObjectNode node, String name, String value) {
    if (value == null) {
      node.putNull(name);
    } else {
      node.put(name, value);
    }
  }
}
