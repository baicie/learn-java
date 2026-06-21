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
import io.aegisops.execution.dto.ExecutionRunResponse;
import io.aegisops.execution.dto.ExecutionVerificationCreateCommand;
import io.aegisops.execution.dto.ExecutionVerificationCreateRequest;
import io.aegisops.execution.dto.ExecutionVerificationRecord;
import io.aegisops.execution.dto.ExecutionVerificationResponse;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionReportService {
  private final ExecutionRequestService executionRequestService;
  private final ExecutionReportRepository repository;
  private final ExecutionReportMarkdownBuilder markdownBuilder;
  private final ExecutionReportJson json;
  private final ExecutionReportHelpers helpers = new ExecutionReportHelpers();

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

    String reportId = helpers.newId("exr");
    String actor = helpers.blankToDefault(request == null ? null : request.generatedBy(), "system");

    appendAuditEvent(
        new AppendAuditEventParams(
            tenantId,
            executionId,
            null,
            "report_generated",
            actor,
            "Execution report generated",
            Map.of("reportId", reportId)));

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
    String summary = helpers.buildSummary(execution);

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

    createReportSections(tenantId, reportId, reportExecution, verifications, auditEvents);

    return get(tenantId, reportId);
  }

  private void createReportSections(
      String tenantId,
      String reportId,
      ExecutionRunResponse execution,
      List<ExecutionVerificationRecord> verifications,
      List<ExecutionAuditEventRecord> auditEvents) {
    int order = 1;
    createSection(
        new CreateSectionParams(
            tenantId,
            reportId,
            order++,
            "summary",
            "Summary",
            helpers.buildSummary(execution),
            Map.of()));
    createSection(
        new CreateSectionParams(
            tenantId,
            reportId,
            order++,
            "steps",
            "Step Summary",
            helpers.buildStepSummary(execution),
            Map.of("stepCount", execution.steps().size())));
    createSection(
        new CreateSectionParams(
            tenantId,
            reportId,
            order++,
            "artifacts",
            "Artifact Summary",
            helpers.buildArtifactSummary(execution),
            Map.of("artifactCount", execution.artifacts().size())));
    createSection(
        new CreateSectionParams(
            tenantId,
            reportId,
            order++,
            "verification",
            "Verification",
            helpers.buildVerificationSummary(verifications),
            Map.of("verificationCount", verifications.size())));
    createSection(
        new CreateSectionParams(
            tenantId,
            reportId,
            order++,
            "audit",
            "Audit Events",
            helpers.buildAuditSummary(auditEvents),
            Map.of("auditEventCount", auditEvents.size())));
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

    String id = helpers.newId("exv");
    String actor = helpers.blankToDefault(request.createdBy(), "system");

    repository.createVerification(
        new ExecutionVerificationCreateCommand(
            id,
            tenantId,
            executionId,
            helpers.blankToNull(request.stepId()),
            normalizeVerificationType(request.verificationType()),
            request.targetType().trim(),
            request.targetId(),
            normalizeVerificationStatus(request.status()),
            request.summary().trim(),
            json.write(request.details()),
            actor));

    appendAuditEvent(
        new AppendAuditEventParams(
            tenantId,
            executionId,
            helpers.blankToNull(request.stepId()),
            "verification_created",
            actor,
            "Execution verification created",
            Map.of("verificationId", id, "status", normalizeVerificationStatus(request.status()))));

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
        .map(helpers::toVerificationResponse)
        .toList();
  }

  public List<ExecutionAuditEventResponse> listAuditEvents(String tenantId, String executionId) {
    executionRequestService.getExecution(tenantId, executionId);
    return repository.listAuditEvents(tenantId, executionId).stream()
        .map(helpers::toAuditEventResponse)
        .toList();
  }

  public void appendAuditEvent(AppendAuditEventParams params) {
    repository.createAuditEvent(
        new ExecutionAuditEventCreateCommand(
            helpers.newId("xae"),
            params.tenantId(),
            params.executionId(),
            helpers.blankToNull(params.stepId()),
            params.eventType(),
            helpers.blankToDefault(params.actor(), "system"),
            params.summary(),
            json.write(params.payload())));
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

  private void createSection(CreateSectionParams params) {
    repository.createSection(
        new ExecutionReportSectionCreateCommand(
            helpers.newId("exrs"),
            params.tenantId(),
            params.reportId(),
            params.order(),
            params.sectionType(),
            params.title(),
            params.content(),
            json.write(params.metadata())));
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
        sections.stream().map(helpers::toSectionResponse).toList(),
        report.createdAt(),
        report.updatedAt());
  }

  private ExecutionReportHelpers getHelpers() {
    return helpers;
  }
}
