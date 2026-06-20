package io.aegisops.execution;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.IncidentCaseCreateCommand;
import io.aegisops.execution.dto.IncidentCaseCreateFromPostmortemRequest;
import io.aegisops.execution.dto.IncidentCasePublishRequest;
import io.aegisops.execution.dto.IncidentCaseRecord;
import io.aegisops.execution.dto.IncidentCaseResolutionStepCreateCommand;
import io.aegisops.execution.dto.IncidentCaseResolutionStepRecord;
import io.aegisops.execution.dto.IncidentCaseResolutionStepResponse;
import io.aegisops.execution.dto.IncidentCaseResponse;
import io.aegisops.execution.dto.IncidentCaseSymptomCreateCommand;
import io.aegisops.execution.dto.IncidentCaseSymptomRecord;
import io.aegisops.execution.dto.IncidentCaseSymptomResponse;
import io.aegisops.execution.dto.IncidentCaseTagCreateCommand;
import io.aegisops.execution.dto.PostmortemActionItemRecord;
import io.aegisops.execution.dto.PostmortemReportRecord;
import io.aegisops.execution.dto.PostmortemSectionRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentCaseService {
  private final IncidentCaseRepository caseRepository;
  private final PostmortemRepository postmortemRepository;
  private final IncidentCaseDraftBuilder draftBuilder;

  public IncidentCaseService(
      IncidentCaseRepository caseRepository,
      PostmortemRepository postmortemRepository,
      IncidentCaseDraftBuilder draftBuilder) {
    this.caseRepository = caseRepository;
    this.postmortemRepository = postmortemRepository;
    this.draftBuilder = draftBuilder;
  }

  @Transactional
  public IncidentCaseResponse createFromPostmortem(
      String tenantId, String postmortemId, IncidentCaseCreateFromPostmortemRequest request) {
    caseRepository
        .findByPostmortem(tenantId, postmortemId)
        .ifPresent(
            existing -> {
              throw new AppException(
                  "INCIDENT_CASE_ALREADY_EXISTS",
                  "Incident case already exists for this postmortem");
            });

    PostmortemReportRecord report =
        postmortemRepository
            .findReport(tenantId, postmortemId)
            .orElseThrow(
                () -> new AppException("POSTMORTEM_NOT_FOUND", "Postmortem report not found"));

    validatePostmortemStatus(report);

    List<PostmortemSectionRecord> sections =
        postmortemRepository.listSections(tenantId, postmortemId);
    List<PostmortemActionItemRecord> actionItems =
        postmortemRepository.listActionItems(tenantId, postmortemId);

    IncidentCaseDraft draft =
        draftBuilder.build(
            report, sections, actionItems, request == null ? List.of() : request.tags());

    String caseId = newId("icase");
    String actor = blankToDefault(request == null ? null : request.createdBy(), "system");

    caseRepository.createCase(
        new IncidentCaseCreateCommand(
            caseId,
            tenantId,
            postmortemId,
            report.incidentId(),
            "draft",
            report.severity(),
            draft.title(),
            draft.summary(),
            draft.rootCause(),
            draft.resolution(),
            draft.prevention(),
            normalizeQualityScore(request == null ? null : request.qualityScore()),
            actor));

    for (IncidentCaseDraft.Symptom symptom : draft.symptoms()) {
      caseRepository.createSymptom(
          new IncidentCaseSymptomCreateCommand(
              newId("icsym"),
              tenantId,
              caseId,
              symptom.symptomType(),
              symptom.name(),
              symptom.description()));
    }

    int order = 1;
    for (IncidentCaseDraft.ResolutionStep step : draft.resolutionSteps()) {
      caseRepository.createResolutionStep(
          new IncidentCaseResolutionStepCreateCommand(
              newId("icstep"),
              tenantId,
              caseId,
              order++,
              step.title(),
              step.description(),
              step.actionType(),
              step.sourceRefId()));
    }

    for (String tag : draft.tags()) {
      caseRepository.createTag(
          new IncidentCaseTagCreateCommand(newId("ictag"), tenantId, caseId, tag));
    }

    return get(tenantId, caseId);
  }

  public IncidentCaseResponse get(String tenantId, String caseId) {
    IncidentCaseRecord record =
        caseRepository
            .findCase(tenantId, caseId)
            .orElseThrow(
                () -> new AppException("INCIDENT_CASE_NOT_FOUND", "Incident case not found"));

    return toResponse(record);
  }

  public IncidentCaseResponse latestByIncident(String tenantId, String incidentId) {
    IncidentCaseRecord record =
        caseRepository
            .findLatestByIncident(tenantId, incidentId)
            .orElseThrow(
                () -> new AppException("INCIDENT_CASE_NOT_FOUND", "Incident case not found"));

    return toResponse(record);
  }

  public List<IncidentCaseResponse> list(String tenantId, String status, String tag, int limit) {
    String normalizedStatus = normalizeListStatus(status);

    return caseRepository
        .listCases(tenantId, normalizedStatus, normalizeTagOrNull(tag), limit)
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public IncidentCaseResponse publish(
      String tenantId, String caseId, IncidentCasePublishRequest request) {
    IncidentCaseRecord record =
        caseRepository
            .findCase(tenantId, caseId)
            .orElseThrow(
                () -> new AppException("INCIDENT_CASE_NOT_FOUND", "Incident case not found"));

    if (!"draft".equals(record.status())) {
      throw new AppException(
          "INCIDENT_CASE_PUBLISH_STATUS_INVALID", "Only draft case can be published");
    }

    if (record.qualityScore() < 60) {
      throw new AppException(
          "INCIDENT_CASE_QUALITY_TOO_LOW",
          "Incident case quality score must be at least 60 to publish");
    }

    String reviewer = request == null ? null : request.reviewer();
    if (reviewer == null || reviewer.isBlank()) {
      throw new AppException("INCIDENT_CASE_REVIEWER_REQUIRED", "Reviewer is required");
    }

    boolean updated = caseRepository.publish(tenantId, caseId, reviewer.trim());
    if (!updated) {
      throw new AppException("INCIDENT_CASE_PUBLISH_FAILED", "Incident case was not published");
    }

    return get(tenantId, caseId);
  }

  @Transactional
  public IncidentCaseResponse archive(String tenantId, String caseId) {
    boolean updated = caseRepository.archive(tenantId, caseId);
    if (!updated) {
      throw new AppException("INCIDENT_CASE_ARCHIVE_FAILED", "Incident case was not archived");
    }
    return get(tenantId, caseId);
  }

  private IncidentCaseResponse toResponse(IncidentCaseRecord record) {
    List<IncidentCaseSymptomResponse> symptoms =
        caseRepository.listSymptoms(record.tenantId(), record.id()).stream()
            .map(this::toSymptomResponse)
            .toList();

    List<IncidentCaseResolutionStepResponse> steps =
        caseRepository.listResolutionSteps(record.tenantId(), record.id()).stream()
            .map(this::toResolutionStepResponse)
            .toList();

    List<String> tags =
        caseRepository.listTags(record.tenantId(), record.id()).stream()
            .map(tag -> tag.tag())
            .toList();

    return new IncidentCaseResponse(
        record.id(),
        record.tenantId(),
        record.sourcePostmortemId(),
        record.incidentId(),
        record.status(),
        record.severity(),
        record.title(),
        record.summary(),
        record.rootCause(),
        record.resolution(),
        record.prevention(),
        record.qualityScore(),
        record.createdBy(),
        record.reviewedBy(),
        record.publishedAt(),
        record.archivedAt(),
        symptoms,
        steps,
        tags,
        record.createdAt(),
        record.updatedAt());
  }

  private IncidentCaseSymptomResponse toSymptomResponse(IncidentCaseSymptomRecord record) {
    return new IncidentCaseSymptomResponse(
        record.id(), record.symptomType(), record.name(), record.description(), record.createdAt());
  }

  private IncidentCaseResolutionStepResponse toResolutionStepResponse(
      IncidentCaseResolutionStepRecord record) {
    return new IncidentCaseResolutionStepResponse(
        record.id(),
        record.stepOrder(),
        record.title(),
        record.description(),
        record.actionType(),
        record.sourceRefId(),
        record.createdAt());
  }

  private void validatePostmortemStatus(PostmortemReportRecord report) {
    if (!List.of("generated", "reviewed").contains(report.status())) {
      throw new AppException(
          "POSTMORTEM_STATUS_INVALID",
          "Only generated or reviewed postmortem can be converted to incident case");
    }
  }

  private int normalizeQualityScore(Integer value) {
    if (value == null) {
      return 60;
    }
    return Math.max(0, Math.min(value, 100));
  }

  private String normalizeListStatus(String value) {
    if (value == null || value.isBlank()) {
      return "published";
    }

    String status = value.trim().toLowerCase();
    if ("all".equals(status)) {
      return null;
    }

    if (!List.of("draft", "published", "archived").contains(status)) {
      throw new AppException("INCIDENT_CASE_STATUS_INVALID", "Invalid incident case status");
    }

    return status;
  }

  private String normalizeTagOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    String tag =
        value
            .trim()
            .toLowerCase()
            .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
            .replaceAll("^-+", "")
            .replaceAll("-+$", "");

    return tag.isBlank() ? null : tag;
  }

  private String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
