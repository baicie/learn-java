package io.aegisops.execution.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record PostmortemSourceBundle(
    IncidentSnapshot incident,
    List<RcaSnapshot> rcaAnalyses,
    List<AiDiagnosisSnapshot> aiDiagnoses,
    List<ExecutionSnapshot> executions,
    List<RollbackSnapshot> rollbackPlans,
    List<TimelineSnapshot> timeline) {
  public record IncidentSnapshot(
      String id,
      String title,
      String status,
      String severity,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}

  public record RcaSnapshot(
      String id,
      String summary,
      String rootCause,
      String confidence,
      OffsetDateTime createdAt) {}

  public record AiDiagnosisSnapshot(
      String id,
      String summary,
      String rootCause,
      String nextSteps,
      OffsetDateTime createdAt) {}

  public record ExecutionSnapshot(
      String id,
      String mode,
      String executionKind,
      String status,
      String summary,
      OffsetDateTime startedAt,
      OffsetDateTime finishedAt) {}

  public record RollbackSnapshot(
      String id,
      String status,
      String reason,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}

  public record TimelineSnapshot(
      String id,
      String eventType,
      String title,
      String content,
      OffsetDateTime eventTime) {}
}
