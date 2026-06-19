package io.aegisops.execution;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.ExecutionArtifactResponse;
import io.aegisops.execution.dto.ExecutionAuditEventCreateCommand;
import io.aegisops.execution.dto.ExecutionAuditEventRecord;
import io.aegisops.execution.dto.ExecutionAuditEventResponse;
import io.aegisops.execution.dto.ExecutionReportCreateCommand;
import io.aegisops.execution.dto.ExecutionReportGenerateRequest;
import io.aegisops.execution.dto.ExecutionReportRecord;
import io.aegisops.execution.dto.ExecutionReportResponse;
import io.aegisops.execution.dto.ExecutionReportSectionCreateCommand;
import io.aegisops.execution.dto.ExecutionReportSectionRecord;
import io.aegisops.execution.dto.ExecutionReportSectionResponse;
import io.aegisops.execution.dto.ExecutionRunResponse;
import io.aegisops.execution.dto.ExecutionVerificationCreateCommand;
import io.aegisops.execution.dto.ExecutionVerificationCreateRequest;
import io.aegisops.execution.dto.ExecutionVerificationRecord;
import io.aegisops.execution.dto.ExecutionVerificationResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionReportService {
  private final ExecutionRequestService executionRequestService;
  private final ExecutionReportRepository repository;
  private final ExecutionReportMarkdownBuilder markdownBuilder;
  private final ExecutionReportJson json;

  public ExecutionReportService(
      ExecutionRequestService executionRequestService,
      ExecutionReportRepository repository,
      ExecutionReportMarkdownBuilder markdownBuilder,
      com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
    this.executionRequestService = executionRequestService;
    this.repository = repository;
    this.markdownBuilder = markdownBuilder;
    this.json = new ExecutionReportJson(objectMapper);
  }

  @Transactional
  public ExecutionReportResponse generate(
      String tenantId, String executionId, ExecutionReportGenerateRequest request) {
    ExecutionRunResponse execution = executionRequestService.getExecution(tenantId, executionId);

    validateTerminal(execution);

    String reportId = newId("exr");
    String actor = blankToDefault(request == null ? null : request.generatedBy(), "system");

    appendAuditEvent(
        tenantId,
        executionId,
        null,
        "report_generated",
        actor,
        "Execution report generated",
        Map.of("reportId", reportId));

    ExecutionRunResponse reportExecution =
        include(request == null ? null : request.includeArtifacts())
            ? execution
            : withoutArtifacts(execution);

    List<ExecutionVerificationRecord> verifications =
        include(request == null ? null : request.includeVerifications())
            ? repository.listVerifications(tenantId, executionId)
            : List.of();

    List<ExecutionAuditEventRecord> auditEvents =
        include(request == null ? null : request.includeAuditEvents())
            ? repository.listAuditEvents(tenantId, executionId)
            : List.of();

    String markdown = markdownBuilder.build(reportExecution, verifications, auditEvents);

    String reportType =
        normalizeReportType(request == null ? null : request.reportType(), execution);
    String title = "Execution Report - " + execution.id();
    String summary = buildSummary(execution);

    repository.createReport(
        new ExecutionReportCreateCommand(
            reportId,
            tenantId,
            executionId,
            reportType,
            "generated",
            title,
            summary,
            markdown,
            actor));

    int order = 1;
    createSection(tenantId, reportId, order++, "summary", "Summary", summary, Map.of());
    createSection(
        tenantId,
        reportId,
        order++,
        "steps",
        "Step Summary",
        buildStepSummary(reportExecution),
        Map.of("stepCount", reportExecution.steps().size()));
    createSection(
        tenantId,
        reportId,
        order++,
        "artifacts",
        "Artifact Summary",
        buildArtifactSummary(reportExecution),
        Map.of("artifactCount", reportExecution.artifacts().size()));
    createSection(
        tenantId,
        reportId,
        order++,
        "verification",
        "Verification",
        buildVerificationSummary(verifications),
        Map.of("verificationCount", verifications.size()));
    createSection(
        tenantId,
        reportId,
        order++,
        "audit",
        "Audit Events",
        buildAuditSummary(auditEvents),
        Map.of("auditEventCount", auditEvents.size()));

    return get(tenantId, reportId);
  }

  public ExecutionReportResponse get(String tenantId, String reportId) {
    ExecutionReportRecord report =
        repository
            .findReport(tenantId, reportId)
            .orElseThrow(
                () -> new AppException("EXECUTION_REPORT_NOT_FOUND", "Execution report not found"));

    return toResponse(report, repository.listSections(tenantId, reportId));
  }

  public ExecutionReportResponse latestByExecution(String tenantId, String executionId) {
    ExecutionReportRecord report =
        repository
            .findLatestReportByExecution(tenantId, executionId)
            .orElseThrow(
                () -> new AppException("EXECUTION_REPORT_NOT_FOUND", "Execution report not found"));

    return get(tenantId, report.id());
  }

  public String markdown(String tenantId, String reportId) {
    return get(tenantId, reportId).markdown();
  }

  @Transactional
  public ExecutionVerificationResponse createVerification(
      String tenantId, String executionId, ExecutionVerificationCreateRequest request) {
    ExecutionRunResponse execution = executionRequestService.getExecution(tenantId, executionId);
    validateVerificationRequest(request);
    validateVerificationStep(execution, request.stepId());

    String id = newId("exv");
    String actor = blankToDefault(request.createdBy(), "system");

    repository.createVerification(
        new ExecutionVerificationCreateCommand(
            id,
            tenantId,
            executionId,
            blankToNull(request.stepId()),
            normalizeVerificationType(request.verificationType()),
            request.targetType().trim(),
            request.targetId(),
            normalizeVerificationStatus(request.status()),
            request.summary().trim(),
            json.write(request.details()),
            actor));

    appendAuditEvent(
        tenantId,
        executionId,
        blankToNull(request.stepId()),
        "verification_created",
        actor,
        "Execution verification created",
        Map.of("verificationId", id, "status", normalizeVerificationStatus(request.status())));

    return listVerifications(tenantId, executionId).stream()
        .filter(item -> item.id().equals(id))
        .findFirst()
        .orElseThrow(
            () ->
                new AppException(
                    "EXECUTION_VERIFICATION_NOT_FOUND", "Execution verification not found"));
  }

  public List<ExecutionVerificationResponse> listVerifications(
      String tenantId, String executionId) {
    executionRequestService.getExecution(tenantId, executionId);
    return repository.listVerifications(tenantId, executionId).stream()
        .map(this::toVerificationResponse)
        .toList();
  }

  public List<ExecutionAuditEventResponse> listAuditEvents(String tenantId, String executionId) {
    executionRequestService.getExecution(tenantId, executionId);
    return repository.listAuditEvents(tenantId, executionId).stream()
        .map(this::toAuditEventResponse)
        .toList();
  }

  public void appendAuditEvent(
      String tenantId,
      String executionId,
      String stepId,
      String eventType,
      String actor,
      String summary,
      Object payload) {
    repository.createAuditEvent(
        new ExecutionAuditEventCreateCommand(
            newId("xae"),
            tenantId,
            executionId,
            blankToNull(stepId),
            eventType,
            blankToDefault(actor, "system"),
            summary,
            json.write(payload)));
  }

  private void validateTerminal(ExecutionRunResponse execution) {
    if (!List.of("succeeded", "failed", "cancelled", "timeout").contains(execution.status())) {
      throw new AppException(
          "EXECUTION_REPORT_STATUS_INVALID", "Only terminal execution can generate report");
    }
  }

  private void validateVerificationRequest(ExecutionVerificationCreateRequest request) {
    if (request == null) {
      throw new AppException(
          "EXECUTION_VERIFICATION_REQUEST_REQUIRED", "Verification request is required");
    }

    if (request.targetType() == null || request.targetType().isBlank()) {
      throw new AppException(
          "EXECUTION_VERIFICATION_TARGET_REQUIRED", "Verification targetType is required");
    }

    if (request.summary() == null || request.summary().isBlank()) {
      throw new AppException(
          "EXECUTION_VERIFICATION_SUMMARY_REQUIRED", "Verification summary is required");
    }

    normalizeVerificationType(request.verificationType());
    normalizeVerificationStatus(request.status());
  }

  private void validateVerificationStep(ExecutionRunResponse execution, String stepId) {
    if (stepId == null || stepId.isBlank()) {
      return;
    }

    boolean exists = execution.steps().stream().anyMatch(step -> step.id().equals(stepId.trim()));

    if (!exists) {
      throw new AppException(
          "EXECUTION_VERIFICATION_STEP_INVALID",
          "Verification stepId does not belong to execution");
    }
  }

  private String normalizeReportType(String value, ExecutionRunResponse execution) {
    if (value != null && !value.isBlank()) {
      String reportType = value.trim().toLowerCase();
      if (!List.of("standard", "rollback", "audit").contains(reportType)) {
        throw new AppException("EXECUTION_REPORT_TYPE_INVALID", "Invalid execution report type");
      }
      return reportType;
    }

    return "rollback".equals(execution.executionKind()) ? "rollback" : "standard";
  }

  private String normalizeVerificationType(String value) {
    String type = value == null || value.isBlank() ? "manual" : value.trim().toLowerCase();
    if (!List.of("before", "after", "manual", "post").contains(type)) {
      throw new AppException("EXECUTION_VERIFICATION_TYPE_INVALID", "Invalid verification type");
    }
    return type;
  }

  private String normalizeVerificationStatus(String value) {
    String status = value == null || value.isBlank() ? "skipped" : value.trim().toLowerCase();
    if (!List.of("passed", "failed", "warn", "skipped").contains(status)) {
      throw new AppException(
          "EXECUTION_VERIFICATION_STATUS_INVALID", "Invalid verification status");
    }
    return status;
  }

  private boolean include(Boolean value) {
    return value == null || value;
  }

  private ExecutionRunResponse withoutArtifacts(ExecutionRunResponse execution) {
    return new ExecutionRunResponse(
        execution.id(),
        execution.tenantId(),
        execution.incidentId(),
        execution.planId(),
        execution.status(),
        execution.mode(),
        execution.requestedBy(),
        execution.runnerId(),
        execution.errorMessage(),
        execution.summary(),
        execution.attempt(),
        execution.maxAttempts(),
        execution.retryOfExecutionId(),
        execution.leaseUntil(),
        execution.heartbeatAt(),
        execution.timeoutSeconds(),
        execution.approvalId(),
        execution.approvalSnapshotJson(),
        execution.planRiskLevel(),
        execution.liveGuardPassedAt(),
        execution.executionKind(),
        execution.rollbackPlanId(),
        execution.rollbackOfExecutionId(),
        execution.steps(),
        List.<ExecutionArtifactResponse>of(),
        execution.startedAt(),
        execution.finishedAt(),
        execution.createdAt(),
        execution.updatedAt());
  }

  private void createSection(
      String tenantId,
      String reportId,
      int order,
      String sectionType,
      String title,
      String content,
      Object metadata) {
    repository.createSection(
        new ExecutionReportSectionCreateCommand(
            newId("exrs"),
            tenantId,
            reportId,
            order,
            sectionType,
            title,
            content,
            json.write(metadata)));
  }

  private String buildSummary(ExecutionRunResponse execution) {
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

  private String buildStepSummary(ExecutionRunResponse execution) {
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

  private String buildArtifactSummary(ExecutionRunResponse execution) {
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

  private String buildVerificationSummary(List<ExecutionVerificationRecord> verifications) {
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

  private String buildAuditSummary(List<ExecutionAuditEventRecord> auditEvents) {
    if (auditEvents.isEmpty()) {
      return "No audit events.";
    }

    StringBuilder builder = new StringBuilder();
    for (ExecutionAuditEventRecord event : auditEvents) {
      builder
          .append("- ")
          .append(event.createdAt())
          .append(" [")
          .append(event.eventType())
          .append("] ")
          .append(event.summary())
          .append(" by ")
          .append(event.actor())
          .append("\n");
    }
    return builder.toString();
  }

  private ExecutionReportResponse toResponse(
      ExecutionReportRecord report, List<ExecutionReportSectionRecord> sections) {
    return new ExecutionReportResponse(
        report.id(),
        report.tenantId(),
        report.executionId(),
        report.reportType(),
        report.status(),
        report.title(),
        report.summary(),
        report.markdown(),
        report.generatedBy(),
        report.generatedAt(),
        sections.stream().map(this::toSectionResponse).toList(),
        report.createdAt(),
        report.updatedAt());
  }

  private ExecutionReportSectionResponse toSectionResponse(ExecutionReportSectionRecord section) {
    return new ExecutionReportSectionResponse(
        section.id(),
        section.sectionOrder(),
        section.sectionType(),
        section.title(),
        section.content(),
        section.metadataJson(),
        section.createdAt());
  }

  private ExecutionVerificationResponse toVerificationResponse(ExecutionVerificationRecord record) {
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

  private ExecutionAuditEventResponse toAuditEventResponse(ExecutionAuditEventRecord record) {
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

  private String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
