package io.aegisops.execution;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.AgentEvalCaseCreateRequest;
import io.aegisops.execution.dto.AgentEvalCaseRecord;
import io.aegisops.execution.dto.AgentEvalCaseResponse;
import io.aegisops.execution.dto.AgentEvalCaseResultRecord;
import io.aegisops.execution.dto.AgentEvalCaseResultResponse;
import io.aegisops.execution.dto.AgentEvalDatasetCreateRequest;
import io.aegisops.execution.dto.AgentEvalDatasetRecord;
import io.aegisops.execution.dto.AgentEvalDatasetResponse;
import io.aegisops.execution.dto.AgentEvalRunCreateRequest;
import io.aegisops.execution.dto.AgentEvalRunRecord;
import io.aegisops.execution.dto.AgentEvalRunResponse;
import io.aegisops.execution.dto.AgentPromptProfileCreateRequest;
import io.aegisops.execution.dto.AgentPromptProfileRecord;
import io.aegisops.execution.dto.AgentPromptProfileResponse;
import io.aegisops.execution.dto.IncidentCaseResponse;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class AgentEvalSupport {
  private static final List<String> DATASET_STATUSES = List.of("draft", "active", "archived");
  private static final List<String> PROFILE_STATUSES = List.of("active", "archived");
  private static final List<String> EVAL_MODES = List.of("mock", "offline_latest_diagnosis");
  private static final List<String> CASE_SOURCE_TYPES =
      List.of("manual", "incident_case", "postmortem");
  private static final List<String> SEVERITIES =
      List.of("info", "low", "medium", "high", "critical");

  void requireDatasetName(String name) {
    if (name == null || name.isBlank()) {
      throw new AppException("AGENT_EVAL_DATASET_NAME_REQUIRED", "Dataset name is required");
    }
  }

  void requireDatasetCreateRequest(AgentEvalDatasetCreateRequest request) {
    if (request == null) {
      throw new AppException(
          "AGENT_EVAL_DATASET_REQUEST_REQUIRED", "Dataset create request is required");
    }
  }

  void requireDatasetId(String datasetId) {
    if (datasetId == null || datasetId.isBlank()) {
      throw new AppException("AGENT_EVAL_RUN_DATASET_REQUIRED", "Dataset id is required");
    }
  }

  void requireRunCreateRequest(AgentEvalRunCreateRequest request) {
    if (request == null) {
      throw new AppException("AGENT_EVAL_RUN_REQUEST_REQUIRED", "Eval run request is required");
    }
  }

  void requireActiveDataset(AgentEvalDatasetRecord dataset) {
    if (!"active".equals(dataset.status())) {
      throw new AppException("AGENT_EVAL_DATASET_NOT_ACTIVE", "Only active dataset can run eval");
    }
  }

  void requireDatasetMutable(AgentEvalDatasetRecord dataset) {
    if (!"draft".equals(dataset.status())) {
      throw new AppException(
          "AGENT_EVAL_DATASET_NOT_MUTABLE", "Only draft dataset can be modified");
    }
  }

  void requireCasesNotEmpty(List<AgentEvalCaseRecord> cases) {
    if (cases.isEmpty()) {
      throw new AppException("AGENT_EVAL_CASES_EMPTY", "Eval dataset has no enabled cases");
    }
  }

  void requirePublishedIncidentCase(String status) {
    if (!"published".equals(status)) {
      throw new AppException(
          "AGENT_EVAL_SOURCE_CASE_STATUS_INVALID",
          "Only published incident case can be converted to eval case");
    }
  }

  void requireActivePromptProfile(String status) {
    if (!"active".equals(status)) {
      throw new AppException(
          "AGENT_PROMPT_PROFILE_NOT_ACTIVE", "Only active prompt profile can run eval");
    }
  }

  void requireRunFinished(boolean updated) {
    if (!updated) {
      throw new AppException("AGENT_EVAL_RUN_FINISH_FAILED", "Eval run was not finished");
    }
  }

  void requireDatasetActivated(boolean updated) {
    if (!updated) {
      throw new AppException("AGENT_EVAL_DATASET_ACTIVATE_FAILED", "Dataset was not activated");
    }
  }

  void requireDatasetArchived(boolean updated) {
    if (!updated) {
      throw new AppException("AGENT_EVAL_DATASET_ARCHIVE_FAILED", "Dataset was not archived");
    }
  }

  void requirePromptProfileArchived(boolean updated) {
    if (!updated) {
      throw new AppException(
          "AGENT_PROMPT_PROFILE_ARCHIVE_FAILED", "Prompt profile was not archived");
    }
  }

  void validateCaseRequest(AgentEvalCaseCreateRequest request) {
    if (request == null) {
      throw new AppException("AGENT_EVAL_CASE_REQUEST_REQUIRED", "Eval case request is required");
    }
    if (request.title() == null || request.title().isBlank()) {
      throw new AppException("AGENT_EVAL_CASE_TITLE_REQUIRED", "Eval case title is required");
    }
    if (request.inputContext() == null || request.inputContext().isBlank()) {
      throw new AppException(
          "AGENT_EVAL_CASE_CONTEXT_REQUIRED", "Eval case input context is required");
    }
  }

  void validatePromptProfileRequest(AgentPromptProfileCreateRequest request) {
    if (request == null) {
      throw new AppException(
          "AGENT_PROMPT_PROFILE_REQUEST_REQUIRED", "Prompt profile request is required");
    }
    if (request.name() == null || request.name().isBlank()) {
      throw new AppException(
          "AGENT_PROMPT_PROFILE_NAME_REQUIRED", "Prompt profile name is required");
    }
    if (request.version() == null || request.version().isBlank()) {
      throw new AppException(
          "AGENT_PROMPT_PROFILE_VERSION_REQUIRED", "Prompt profile version is required");
    }
    if (request.systemPrompt() == null || request.systemPrompt().isBlank()) {
      throw new AppException("AGENT_PROMPT_SYSTEM_PROMPT_REQUIRED", "System prompt is required");
    }
    if (request.diagnosisPromptTemplate() == null || request.diagnosisPromptTemplate().isBlank()) {
      throw new AppException(
          "AGENT_PROMPT_TEMPLATE_REQUIRED", "Diagnosis prompt template is required");
    }
  }

  String normalizeDatasetStatusOrNull(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    String value = status.trim().toLowerCase();
    if (!DATASET_STATUSES.contains(value)) {
      throw new AppException("AGENT_EVAL_DATASET_STATUS_INVALID", "Invalid dataset status");
    }
    return value;
  }

  String normalizePromptProfileStatusOrNull(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    String value = status.trim().toLowerCase();
    if (!PROFILE_STATUSES.contains(value)) {
      throw new AppException(
          "AGENT_PROMPT_PROFILE_STATUS_INVALID", "Invalid prompt profile status");
    }
    return value;
  }

  String normalizeEvalMode(String mode) {
    String value =
        mode == null || mode.isBlank() ? "offline_latest_diagnosis" : mode.trim().toLowerCase();
    if (!EVAL_MODES.contains(value)) {
      throw new AppException("AGENT_EVAL_RUN_MODE_INVALID", "Invalid eval run mode");
    }
    return value;
  }

  String normalizeCaseSourceType(String sourceType) {
    String value =
        sourceType == null || sourceType.isBlank() ? "manual" : sourceType.trim().toLowerCase();
    if (!CASE_SOURCE_TYPES.contains(value)) {
      throw new AppException(
          "AGENT_EVAL_CASE_SOURCE_TYPE_INVALID", "Invalid eval case source type");
    }
    return value;
  }

  String normalizeSeverity(String severity) {
    if (severity == null || severity.isBlank()) {
      return null;
    }
    String value = severity.trim().toLowerCase();
    if (!SEVERITIES.contains(value)) {
      return null;
    }
    return value;
  }

  String buildInputContext(IncidentCaseResponse incidentCase) {
    return """
        Title: %s
        Severity: %s
        Summary: %s
        Root Cause: %s
        Resolution: %s
        Prevention: %s
        Tags: %s
        """
        .formatted(
            value(incidentCase.title()),
            value(incidentCase.severity()),
            value(incidentCase.summary()),
            value(incidentCase.rootCause()),
            value(incidentCase.resolution()),
            value(incidentCase.prevention()),
            String.join(", ", incidentCase.tags()));
  }

  @SafeVarargs
  final List<String> mergeLists(List<String>... lists) {
    return Arrays.stream(lists)
        .filter(list -> list != null)
        .flatMap(list -> list.stream())
        .filter(item -> item != null && !item.isBlank())
        .map(text -> text.trim())
        .distinct()
        .toList();
  }

  List<String> listOrEmpty(List<String> values) {
    if (values == null) {
      return List.of();
    }
    return values.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(text -> text.trim())
        .distinct()
        .toList();
  }

  String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  String value(String value) {
    return value == null ? "" : value;
  }

  double round(double value) {
    return Math.round(value * 10000.0d) / 10000.0d;
  }

  String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }

  AgentEvalDatasetResponse toDatasetResponse(AgentEvalDatasetRecord record) {
    return new AgentEvalDatasetResponse(
        record.id(),
        record.name(),
        record.description(),
        record.status(),
        record.createdBy(),
        record.createdAt(),
        record.updatedAt());
  }

  AgentEvalCaseResponse toCaseResponse(AgentEvalCaseRecord record) {
    return new AgentEvalCaseResponse(
        record.id(),
        record.datasetId(),
        record.sourceType(),
        record.sourceId(),
        record.incidentId(),
        record.title(),
        record.severity(),
        record.inputContext(),
        record.expectedRootCause(),
        record.expectedKeywordsJson(),
        record.expectedActionsJson(),
        record.forbiddenActionsJson(),
        record.tagsJson(),
        record.enabled(),
        record.createdBy(),
        record.createdAt(),
        record.updatedAt());
  }

  AgentPromptProfileResponse toPromptProfileResponse(AgentPromptProfileRecord record) {
    return new AgentPromptProfileResponse(
        record.id(),
        record.name(),
        record.version(),
        record.status(),
        record.systemPrompt(),
        record.diagnosisPromptTemplate(),
        record.metadataJson(),
        record.createdBy(),
        record.createdAt(),
        record.updatedAt());
  }

  AgentEvalRunResponse toRunResponse(AgentEvalRunRecord record) {
    return new AgentEvalRunResponse(
        record.id(),
        record.datasetId(),
        record.promptProfileId(),
        record.mode(),
        record.status(),
        record.totalCases(),
        record.passedCases(),
        record.failedCases(),
        record.averageScore(),
        record.summary(),
        record.createdBy(),
        record.startedAt(),
        record.finishedAt(),
        record.createdAt(),
        record.updatedAt());
  }

  AgentEvalCaseResultResponse toCaseResultResponse(AgentEvalCaseResultRecord record) {
    return new AgentEvalCaseResultResponse(
        record.id(),
        record.runId(),
        record.caseId(),
        record.actualDiagnosisId(),
        record.actualSummary(),
        record.actualRootCause(),
        record.actualRecommendation(),
        record.score(),
        record.rootCauseScore(),
        record.keywordScore(),
        record.actionScore(),
        record.safetyScore(),
        record.passed(),
        record.detailsJson(),
        record.createdAt());
  }
}
