package io.aegisops.execution;

import io.aegisops.execution.dto.ExecutionAuditEventRecord;
import io.aegisops.execution.dto.ExecutionAuditEventResponse;
import io.aegisops.execution.dto.ExecutionReportRecord;
import io.aegisops.execution.dto.ExecutionReportResponse;
import io.aegisops.execution.dto.ExecutionReportSectionRecord;
import io.aegisops.execution.dto.ExecutionReportSectionResponse;
import io.aegisops.execution.dto.ExecutionRunResponse;
import io.aegisops.execution.dto.ExecutionVerificationRecord;
import io.aegisops.execution.dto.ExecutionVerificationResponse;
import java.util.List;
import java.util.UUID;

/** Private helper for ExecutionReportService, isolated to avoid Checkstyle file length. */
class ExecutionReportHelpers {

  String buildSummary(ExecutionRunResponse execution) {
    return "Execution "
        + execution.id()
        + " finished with status "
        + execution.status()
        + ". Mode="
        + execution.mode()
        + ", Kind="
        + execution.executionKind()
        + ".";
  }

  String buildStepSummary(ExecutionRunResponse execution) {
    StringBuilder builder = new StringBuilder();
    for (var step : execution.steps()) {
      builder
          .append(step.sequenceNo())
          .append(". ")
          .append(step.name())
          .append(" [")
          .append(step.actionType())
          .append("] -> ")
          .append(step.status())
          .append("\n");
    }
    return builder.isEmpty() ? "No steps." : builder.toString();
  }

  String buildArtifactSummary(ExecutionRunResponse execution) {
    StringBuilder builder = new StringBuilder();
    for (var artifact : execution.artifacts()) {
      builder
          .append("- ")
          .append(artifact.name())
          .append(" (")
          .append(artifact.artifactType())
          .append(")")
          .append(" step=")
          .append(artifact.stepId())
          .append("\n");
    }
    return builder.isEmpty() ? "No artifacts." : builder.toString();
  }

  String buildVerificationSummary(List<ExecutionVerificationRecord> verifications) {
    if (verifications.isEmpty()) {
      return "No verification records.";
    }
    StringBuilder builder = new StringBuilder();
    for (ExecutionVerificationRecord verification : verifications) {
      builder
          .append("- ")
          .append(verification.verificationType())
          .append(" ")
          .append(verification.targetType())
          .append(":")
          .append(verification.targetId())
          .append(" -> ")
          .append(verification.status())
          .append(" - ")
          .append(verification.summary())
          .append("\n");
    }
    return builder.toString();
  }

  String buildAuditSummary(List<ExecutionAuditEventRecord> auditEvents) {
    if (auditEvents.isEmpty()) {
      return "No audit events.";
    }
    StringBuilder builder = new StringBuilder();
    for (ExecutionAuditEventRecord event : auditEvents) {
      builder
          .append("- ")
          .append(event.eventType())
          .append(" by ")
          .append(event.actor())
          .append(": ")
          .append(event.summary())
          .append("\n");
    }
    return builder.toString();
  }

  ExecutionReportResponse toReportResponse(ExecutionReportRecord record) {
    return new ExecutionReportResponse(
        record.id(),
        record.tenantId(),
        record.executionId(),
        record.reportType(),
        record.status(),
        record.title(),
        record.summary(),
        record.markdown(),
        record.generatedBy(),
        record.generatedAt(),
        List.of(),
        record.createdAt(),
        record.updatedAt());
  }

  ExecutionReportSectionResponse toSectionResponse(ExecutionReportSectionRecord record) {
    return new ExecutionReportSectionResponse(
        record.id(),
        record.sectionOrder(),
        record.sectionType(),
        record.title(),
        record.content(),
        record.metadataJson(),
        record.createdAt());
  }

  ExecutionVerificationResponse toVerificationResponse(ExecutionVerificationRecord record) {
    return new ExecutionVerificationResponse(
        record.id(),
        record.executionId(),
        record.stepId(),
        record.verificationType(),
        record.targetType(),
        record.targetId(),
        record.status(),
        record.summary(),
        record.detailsJson(),
        record.createdBy(),
        record.createdAt());
  }

  ExecutionAuditEventResponse toAuditEventResponse(ExecutionAuditEventRecord record) {
    return new ExecutionAuditEventResponse(
        record.id(),
        record.executionId(),
        record.stepId(),
        record.eventType(),
        record.actor(),
        record.summary(),
        record.payloadJson(),
        record.createdAt());
  }

  String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
