package io.aegisops.execution;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.PostmortemActionItemCreateCommand;
import io.aegisops.execution.dto.PostmortemActionItemCreateRequest;
import io.aegisops.execution.dto.PostmortemActionItemRecord;
import io.aegisops.execution.dto.PostmortemActionItemResponse;
import io.aegisops.execution.dto.PostmortemActionItemStatusRequest;
import io.aegisops.execution.dto.PostmortemGenerateRequest;
import io.aegisops.execution.dto.PostmortemReportCreateCommand;
import io.aegisops.execution.dto.PostmortemReportRecord;
import io.aegisops.execution.dto.PostmortemReportResponse;
import io.aegisops.execution.dto.PostmortemSectionCreateCommand;
import io.aegisops.execution.dto.PostmortemSectionRecord;
import io.aegisops.execution.dto.PostmortemSectionResponse;
import io.aegisops.execution.dto.PostmortemSourceBundle;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostmortemService {
  private final PostmortemRepository repository;
  private final PostmortemSourceRepository sourceRepository;
  private final PostmortemDraftBuilder draftBuilder;
  private final PostmortemMarkdownBuilder markdownBuilder;
  private final PostmortemJson json;

  public PostmortemService(
      PostmortemRepository repository,
      PostmortemSourceRepository sourceRepository,
      PostmortemDraftBuilder draftBuilder,
      PostmortemMarkdownBuilder markdownBuilder,
      com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
    this.repository = repository;
    this.sourceRepository = sourceRepository;
    this.draftBuilder = draftBuilder;
    this.markdownBuilder = markdownBuilder;
    this.json = new PostmortemJson(objectMapper);
  }

  @Transactional
  public PostmortemReportResponse generate(
      String tenantId, String incidentId, PostmortemGenerateRequest request) {
    PostmortemSourceBundle source =
        sourceRepository
            .load(tenantId, incidentId)
            .orElseThrow(() -> new AppException("INCIDENT_NOT_FOUND", "Incident not found"));

    PostmortemSourceBundle filtered = filterSource(source, request);
    PostmortemDraft draft =
        draftBuilder.build(
            filtered, include(request == null ? null : request.generateActionItems()));

    String markdown =
        markdownBuilder.build(
            filtered,
            draft.summary(),
            draft.impact(),
            draft.rootCause(),
            draft.detection(),
            draft.resolution(),
            draft.prevention(),
            draft.actionItems());

    String postmortemId = newId("pmr");
    String actor = blankToDefault(request == null ? null : request.generatedBy(), "system");

    repository.createReport(
        new PostmortemReportCreateCommand(
            postmortemId,
            tenantId,
            incidentId,
            "generated",
            normalizeSeverity(filtered.incident().severity()),
            draft.title(),
            draft.summary(),
            draft.impact(),
            draft.rootCause(),
            draft.detection(),
            draft.resolution(),
            draft.prevention(),
            markdown,
            json.write(filtered),
            actor));

    int order = 1;
    createSection(
        tenantId, postmortemId, order++, "summary", "Incident Summary", draft.summary(), Map.of());
    createSection(tenantId, postmortemId, order++, "impact", "Impact", draft.impact(), Map.of());
    createSection(
        tenantId,
        postmortemId,
        order++,
        "timeline",
        "Timeline",
        buildTimelineSection(filtered),
        Map.of("count", filtered.timeline().size()));
    createSection(
        tenantId, postmortemId, order++, "root_cause", "Root Cause", draft.rootCause(), Map.of());
    createSection(
        tenantId, postmortemId, order++, "detection", "Detection", draft.detection(), Map.of());
    createSection(
        tenantId, postmortemId, order++, "resolution", "Resolution", draft.resolution(), Map.of());
    createSection(
        tenantId, postmortemId, order++, "prevention", "Prevention", draft.prevention(), Map.of());

    if (include(request == null ? null : request.generateActionItems())) {
      for (String item : draft.actionItems()) {
        repository.createActionItem(
            new PostmortemActionItemCreateCommand(
                newId("pmai"),
                tenantId,
                postmortemId,
                item,
                null,
                null,
                "medium",
                "open",
                null,
                "ai_diagnosis",
                null,
                actor));
      }
    }

    return get(tenantId, postmortemId);
  }

  public PostmortemReportResponse get(String tenantId, String postmortemId) {
    PostmortemReportRecord report =
        repository
            .findReport(tenantId, postmortemId)
            .orElseThrow(
                () -> new AppException("POSTMORTEM_NOT_FOUND", "Postmortem report not found"));

    return toResponse(
        report,
        repository.listSections(tenantId, postmortemId),
        repository.listActionItems(tenantId, postmortemId));
  }

  public PostmortemReportResponse latestByIncident(String tenantId, String incidentId) {
    PostmortemReportRecord report =
        repository
            .findLatestByIncident(tenantId, incidentId)
            .orElseThrow(
                () -> new AppException("POSTMORTEM_NOT_FOUND", "Postmortem report not found"));

    return get(tenantId, report.id());
  }

  public String markdown(String tenantId, String postmortemId) {
    return get(tenantId, postmortemId).markdown();
  }

  @Transactional
  public PostmortemActionItemResponse createActionItem(
      String tenantId, String postmortemId, PostmortemActionItemCreateRequest request) {
    repository
        .findReport(tenantId, postmortemId)
        .orElseThrow(() -> new AppException("POSTMORTEM_NOT_FOUND", "Postmortem report not found"));

    validateActionItemRequest(request);

    String id = newId("pmai");

    repository.createActionItem(
        new PostmortemActionItemCreateCommand(
            id,
            tenantId,
            postmortemId,
            request.title().trim(),
            request.description(),
            request.owner(),
            normalizePriority(request.priority()),
            "open",
            request.dueDate(),
            normalizeSourceType(request.sourceType()),
            request.sourceRefId(),
            blankToDefault(request.createdBy(), "system")));

    return repository
        .findActionItem(tenantId, id)
        .map(this::toActionItemResponse)
        .orElseThrow(
            () -> new AppException("POSTMORTEM_ACTION_ITEM_NOT_FOUND", "Action item not found"));
  }

  public List<PostmortemActionItemResponse> listActionItems(String tenantId, String postmortemId) {
    repository
        .findReport(tenantId, postmortemId)
        .orElseThrow(() -> new AppException("POSTMORTEM_NOT_FOUND", "Postmortem report not found"));

    return repository.listActionItems(tenantId, postmortemId).stream()
        .map(this::toActionItemResponse)
        .toList();
  }

  @Transactional
  public PostmortemActionItemResponse updateActionItemStatus(
      String tenantId, String actionItemId, PostmortemActionItemStatusRequest request) {
    String status = normalizeActionStatus(request == null ? null : request.status());

    boolean updated = repository.updateActionItemStatus(tenantId, actionItemId, status);
    if (!updated) {
      throw new AppException("POSTMORTEM_ACTION_ITEM_UPDATE_FAILED", "Action item was not updated");
    }

    return repository
        .findActionItem(tenantId, actionItemId)
        .map(this::toActionItemResponse)
        .orElseThrow(
            () -> new AppException("POSTMORTEM_ACTION_ITEM_NOT_FOUND", "Action item not found"));
  }

  private PostmortemSourceBundle filterSource(
      PostmortemSourceBundle source, PostmortemGenerateRequest request) {
    if (request == null) {
      return source;
    }

    return new PostmortemSourceBundle(
        source.incident(),
        include(request.includeRca()) ? source.rcaAnalyses() : List.of(),
        include(request.includeAiDiagnosis()) ? source.aiDiagnoses() : List.of(),
        include(request.includeExecutions()) ? source.executions() : List.of(),
        include(request.includeRollback()) ? source.rollbackPlans() : List.of(),
        include(request.includeTimeline()) ? source.timeline() : List.of());
  }

  private String buildTimelineSection(PostmortemSourceBundle source) {
    if (source.timeline().isEmpty()) {
      return "No timeline events.";
    }

    StringBuilder builder = new StringBuilder();
    for (var event : source.timeline()) {
      builder
          .append("- ")
          .append(event.eventTime())
          .append(" [")
          .append(event.eventType())
          .append("] ")
          .append(event.title())
          .append(": ")
          .append(event.content())
          .append("\n");
    }
    return builder.toString();
  }

  private void createSection(
      String tenantId,
      String postmortemId,
      int order,
      String sectionType,
      String title,
      String content,
      Object metadata) {
    repository.createSection(
        new PostmortemSectionCreateCommand(
            newId("pms"),
            tenantId,
            postmortemId,
            order,
            sectionType,
            title,
            content,
            json.write(metadata)));
  }

  private void validateActionItemRequest(PostmortemActionItemCreateRequest request) {
    if (request == null) {
      throw new AppException(
          "POSTMORTEM_ACTION_ITEM_REQUEST_REQUIRED", "Action item request is required");
    }

    if (request.title() == null || request.title().isBlank()) {
      throw new AppException(
          "POSTMORTEM_ACTION_ITEM_TITLE_REQUIRED", "Action item title is required");
    }

    normalizePriority(request.priority());
    normalizeSourceType(request.sourceType());
  }

  private String normalizeSeverity(String severity) {
    if (severity == null || severity.isBlank()) {
      return "medium";
    }

    String value = severity.trim().toLowerCase();
    if (!List.of("info", "low", "medium", "high", "critical").contains(value)) {
      return "medium";
    }
    return value;
  }

  private String normalizePriority(String priority) {
    String value =
        priority == null || priority.isBlank() ? "medium" : priority.trim().toLowerCase();
    if (!List.of("low", "medium", "high", "critical").contains(value)) {
      throw new AppException(
          "POSTMORTEM_ACTION_ITEM_PRIORITY_INVALID", "Invalid action item priority");
    }
    return value;
  }

  private String normalizeSourceType(String sourceType) {
    String value =
        sourceType == null || sourceType.isBlank() ? "manual" : sourceType.trim().toLowerCase();
    if (!List.of("manual", "rca", "ai_diagnosis", "execution", "rollback").contains(value)) {
      throw new AppException(
          "POSTMORTEM_ACTION_ITEM_SOURCE_TYPE_INVALID", "Invalid action item source type");
    }
    return value;
  }

  private String normalizeActionStatus(String status) {
    String value = status == null || status.isBlank() ? "" : status.trim().toLowerCase();
    if (!List.of("open", "in_progress", "done", "cancelled").contains(value)) {
      throw new AppException("POSTMORTEM_ACTION_ITEM_STATUS_INVALID", "Invalid action item status");
    }
    return value;
  }

  private boolean include(Boolean value) {
    return value == null || value;
  }

  private PostmortemReportResponse toResponse(
      PostmortemReportRecord report,
      List<PostmortemSectionRecord> sections,
      List<PostmortemActionItemRecord> actionItems) {
    return new PostmortemReportResponse(
        report.id(),
        report.tenantId(),
        report.incidentId(),
        report.status(),
        report.severity(),
        report.title(),
        report.summary(),
        report.impact(),
        report.rootCause(),
        report.detection(),
        report.resolution(),
        report.prevention(),
        report.markdown(),
        report.sourceSnapshotJson(),
        report.generatedBy(),
        report.generatedAt(),
        sections.stream().map(this::toSectionResponse).toList(),
        actionItems.stream().map(this::toActionItemResponse).toList(),
        report.createdAt(),
        report.updatedAt());
  }

  private PostmortemSectionResponse toSectionResponse(PostmortemSectionRecord section) {
    return new PostmortemSectionResponse(
        section.id(),
        section.sectionOrder(),
        section.sectionType(),
        section.title(),
        section.content(),
        section.metadataJson(),
        section.createdAt());
  }

  private PostmortemActionItemResponse toActionItemResponse(PostmortemActionItemRecord item) {
    return new PostmortemActionItemResponse(
        item.id(),
        item.title(),
        item.description(),
        item.owner(),
        item.priority(),
        item.status(),
        item.dueDate(),
        item.sourceType(),
        item.sourceRefId(),
        item.createdBy(),
        item.createdAt(),
        item.updatedAt());
  }

  private String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
