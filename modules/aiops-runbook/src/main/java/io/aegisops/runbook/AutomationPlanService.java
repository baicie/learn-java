package io.aegisops.runbook;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.runbook.dto.AutomationPlanCreateCommand;
import io.aegisops.runbook.dto.AutomationPlanRecord;
import io.aegisops.runbook.dto.AutomationPlanResponse;
import io.aegisops.runbook.dto.AutomationPlanStepRecord;
import io.aegisops.runbook.dto.AutomationPlanStepResponse;
import io.aegisops.runbook.dto.CreateRunbookRequest;
import io.aegisops.runbook.dto.CreateRunbookStepRequest;
import io.aegisops.runbook.dto.IncidentForPlanRecord;
import io.aegisops.runbook.dto.RecommendPlanRequest;
import io.aegisops.runbook.dto.RunbookCreateCommand;
import io.aegisops.runbook.dto.RunbookRecord;
import io.aegisops.runbook.dto.RunbookResponse;
import io.aegisops.runbook.dto.RunbookStepTemplateCreateCommand;
import io.aegisops.runbook.dto.RunbookStepTemplateRecord;
import io.aegisops.runbook.dto.TimelineCreateCommand;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for runbook management and automation plan recommendation.
 *
 * <p>Phase5.0: only creates draft automation plans. Never executes actions.
 */
@Service
public class AutomationPlanService {
  private static final String TIMELINE_EVENT = "automation_plan_recommended";
  private static final String SYSTEM_USER = "system";

  private final RunbookRepository repository;
  private final RunbookJson json;
  private final RunbookDraftBuilder draftBuilder;

  public AutomationPlanService(RunbookRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.json = new RunbookJson(objectMapper);
    this.draftBuilder = new RunbookDraftBuilder(repository, json);
  }

  public List<RunbookResponse> listRunbooks(String tenantId, boolean includeDisabled) {
    return repository.listRunbooks(tenantId, includeDisabled).stream()
        .map(runbook -> toRunbookResponse(runbook, repository.listRunbookSteps(runbook.id())))
        .toList();
  }

  public RunbookResponse getRunbook(String tenantId, String runbookId) {
    RunbookRecord runbook =
        repository
            .findRunbook(tenantId, runbookId)
            .orElseThrow(() -> new AppException("RUNBOOK_NOT_FOUND", "Runbook not found"));

    return toRunbookResponse(runbook, repository.listRunbookSteps(runbook.id()));
  }

  @Transactional
  public RunbookResponse createRunbook(String tenantId, CreateRunbookRequest request) {
    validateCreateRunbookRequest(request);

    String runbookId = newId("rb");
    repository.createRunbook(buildRunbookCreateCommand(runbookId, tenantId, request));

    List<CreateRunbookStepRequest> stepRequests =
        request.steps() == null || request.steps().isEmpty()
            ? List.of(defaultManualStep())
            : request.steps();

    repository.createRunbookSteps(toStepTemplateCreateCommands(runbookId, stepRequests));

    return getRunbook(tenantId, runbookId);
  }

  @Transactional
  public RunbookResponse setRunbookEnabled(String tenantId, String runbookId, boolean enabled) {
    RunbookRecord runbook =
        repository
            .findRunbook(tenantId, runbookId)
            .orElseThrow(() -> new AppException("RUNBOOK_NOT_FOUND", "Runbook not found"));

    if (runbook.tenantId() == null || !tenantId.equals(runbook.tenantId())) {
      throw new AppException(
          "RUNBOOK_GLOBAL_READ_ONLY", "Global runbook cannot be enabled or disabled by tenant API");
    }

    boolean updated = repository.setRunbookEnabled(tenantId, runbookId, enabled);
    if (!updated) {
      throw new AppException("RUNBOOK_UPDATE_FAILED", "Runbook enabled status was not updated");
    }

    return getRunbook(tenantId, runbookId);
  }

  public AutomationPlanResponse latestPlan(String tenantId, String incidentId) {
    ensureIncidentExists(tenantId, incidentId);

    AutomationPlanRecord plan =
        repository
            .findLatestPlan(tenantId, incidentId)
            .orElseThrow(
                () -> new AppException("AUTOMATION_PLAN_NOT_FOUND", "Automation plan not found"));

    return toPlanResponse(plan, repository.listPlanSteps(plan.id()));
  }

  public AutomationPlanResponse getPlan(String tenantId, String planId) {
    AutomationPlanRecord plan =
        repository
            .findPlan(tenantId, planId)
            .orElseThrow(
                () -> new AppException("AUTOMATION_PLAN_NOT_FOUND", "Automation plan not found"));

    return toPlanResponse(plan, repository.listPlanSteps(plan.id()));
  }

  @Transactional
  public AutomationPlanResponse recommend(
      String tenantId, String incidentId, RecommendPlanRequest request) {
    RecommendPlanRequest normalized = request == null ? new RecommendPlanRequest(false) : request;

    IncidentForPlanRecord incident = loadIncident(tenantId, incidentId);

    if (!normalized.forceEnabled()) {
      AutomationPlanResponse reused = findReusableLatestPlan(tenantId, incident);
      if (reused != null) {
        return reused;
      }
    }

    RunbookDraftBuilder.AutomationPlanDraft draft = draftBuilder.build(tenantId, incident);
    persistDraft(incident, draft);

    return loadPlanResponse(tenantId, draft.plan().id());
  }

  private IncidentForPlanRecord loadIncident(String tenantId, String incidentId) {
    return repository
        .findIncident(tenantId, incidentId)
        .orElseThrow(() -> new AppException("INCIDENT_NOT_FOUND", "Incident not found"));
  }

  private AutomationPlanResponse findReusableLatestPlan(
      String tenantId, IncidentForPlanRecord incident) {
    var latest = repository.findLatestPlan(tenantId, incident.id());
    if (latest.isEmpty()) {
      return null;
    }
    AutomationPlanRecord record = latest.get();
    if (!isReusableLatest(incident, record)) {
      return null;
    }
    return toPlanResponse(record, repository.listPlanSteps(record.id()));
  }

  private void persistDraft(
      IncidentForPlanRecord incident, RunbookDraftBuilder.AutomationPlanDraft draft) {
    repository.createPlan(draft.plan());
    repository.createPlanSteps(draft.steps());
    repository.addTimeline(buildTimelineCommand(incident, draft));
  }

  private TimelineCreateCommand buildTimelineCommand(
      IncidentForPlanRecord incident, RunbookDraftBuilder.AutomationPlanDraft draft) {
    AutomationPlanCreateCommand plan = draft.plan();
    return new TimelineCreateCommand(
        newId("tl"),
        incident.id(),
        OffsetDateTime.now(),
        TIMELINE_EVENT,
        "Automation plan recommended",
        plan.summary(),
        SYSTEM_USER,
        json.write(
            Map.of(
                "automationPlanId",
                plan.id(),
                "source",
                RunbookDraftBuilder.SOURCE,
                "runbookId",
                plan.runbookId() == null ? "" : plan.runbookId(),
                "riskLevel",
                plan.riskLevel(),
                "confidence",
                plan.confidence())));
  }

  private AutomationPlanResponse loadPlanResponse(String tenantId, String planId) {
    AutomationPlanRecord saved =
        repository
            .findPlan(tenantId, planId)
            .orElseThrow(
                () ->
                    new AppException(
                        "AUTOMATION_PLAN_NOT_FOUND", "Automation plan not found after save"));
    return toPlanResponse(saved, repository.listPlanSteps(planId));
  }

  private RunbookCreateCommand buildRunbookCreateCommand(
      String runbookId, String tenantId, CreateRunbookRequest request) {
    return new RunbookCreateCommand(
        runbookId,
        tenantId,
        request.name().trim(),
        request.description(),
        blankToDefault(request.category(), "general"),
        normalizedRisk(request.riskLevel()),
        true,
        json.write(request.matchers() == null ? Map.of() : request.matchers()),
        json.write(request.variables() == null ? Map.of() : request.variables()));
  }

  private List<RunbookStepTemplateCreateCommand> toStepTemplateCreateCommands(
      String runbookId, List<CreateRunbookStepRequest> stepRequests) {
    List<RunbookStepTemplateCreateCommand> commands = new ArrayList<>();
    Set<Integer> usedSequences = new HashSet<>();
    int nextSequence = 1;

    for (CreateRunbookStepRequest step : stepRequests) {
      int sequenceNo;
      if (step.sequenceNo() == null) {
        sequenceNo = nextAvailableSequence(usedSequences, nextSequence);
      } else {
        sequenceNo = step.sequenceNo();
      }
      validateSequenceNo(sequenceNo, usedSequences);
      usedSequences.add(sequenceNo);
      nextSequence = Math.max(nextSequence, sequenceNo + 1);
      commands.add(toStepTemplateCreateCommand(runbookId, step, sequenceNo));
    }

    return commands;
  }

  private int nextAvailableSequence(Set<Integer> usedSequences, int start) {
    int sequence = start;
    while (usedSequences.contains(sequence)) {
      sequence++;
    }
    return sequence;
  }

  private void validateSequenceNo(int sequenceNo, Set<Integer> usedSequences) {
    if (sequenceNo <= 0) {
      throw new AppException(
          "RUNBOOK_STEP_SEQUENCE_INVALID", "Runbook step sequenceNo must be greater than 0");
    }
    if (usedSequences.contains(sequenceNo)) {
      throw new AppException(
          "RUNBOOK_STEP_SEQUENCE_DUPLICATED",
          "Runbook step sequenceNo must be unique within one runbook");
    }
  }

  private RunbookStepTemplateCreateCommand toStepTemplateCreateCommand(
      String runbookId, CreateRunbookStepRequest step, int sequenceNo) {
    return new RunbookStepTemplateCreateCommand(
        newId("rbstep"),
        runbookId,
        sequenceNo,
        blankToDefault(step.name(), "Manual verification"),
        normalizedActionType(step.actionType()),
        normalizedTargetType(step.targetType()),
        step.commandTemplate(),
        step.description(),
        step.expectedResult(),
        step.rollbackHint(),
        step.requiresApproval() == null || step.requiresApproval(),
        step.timeoutSeconds() == null ? 300 : step.timeoutSeconds(),
        json.write(step.metadata() == null ? Map.of() : step.metadata()));
  }

  private CreateRunbookStepRequest defaultManualStep() {
    return new CreateRunbookStepRequest(
        1,
        "Manual verification",
        "manual",
        "human",
        "",
        "Verify incident context and decide whether remediation is safe.",
        "Operator confirms next action.",
        "No automated rollback.",
        true,
        300,
        Map.of());
  }

  private boolean isReusableLatest(IncidentForPlanRecord incident, AutomationPlanRecord latest) {
    OffsetDateTime baseline = incident.lastSeenAt();
    if (baseline == null) {
      baseline = incident.updatedAt();
    }
    if (baseline == null) {
      baseline = incident.createdAt();
    }

    return baseline != null && latest.createdAt() != null && !latest.createdAt().isBefore(baseline);
  }

  private void ensureIncidentExists(String tenantId, String incidentId) {
    repository
        .findIncident(tenantId, incidentId)
        .orElseThrow(() -> new AppException("INCIDENT_NOT_FOUND", "Incident not found"));
  }

  private AutomationPlanResponse toPlanResponse(
      AutomationPlanRecord plan, List<AutomationPlanStepRecord> steps) {
    return new AutomationPlanResponse(
        plan.id(),
        plan.tenantId(),
        plan.incidentId(),
        plan.runbookId(),
        plan.aiDiagnosisId(),
        plan.rcaAnalysisId(),
        plan.source(),
        plan.status(),
        plan.riskLevel(),
        plan.confidence(),
        plan.title(),
        plan.summary(),
        plan.evidenceJson(),
        plan.createdBy(),
        steps.stream().map(step -> toStepResponse(step)).toList(),
        plan.createdAt(),
        plan.updatedAt());
  }

  private AutomationPlanStepResponse toStepResponse(AutomationPlanStepRecord step) {
    return new AutomationPlanStepResponse(
        step.id(),
        step.planId(),
        step.sequenceNo(),
        step.name(),
        step.actionType(),
        step.targetType(),
        step.actionPayloadJson(),
        step.description(),
        step.expectedResult(),
        step.rollbackHint(),
        step.requiresApproval(),
        step.status(),
        step.createdAt());
  }

  private RunbookResponse toRunbookResponse(
      RunbookRecord runbook, List<RunbookStepTemplateRecord> steps) {
    return new RunbookResponse(
        runbook.id(),
        runbook.tenantId(),
        runbook.name(),
        runbook.description(),
        runbook.category(),
        runbook.riskLevel(),
        runbook.enabled(),
        runbook.matchersJson(),
        runbook.variablesJson(),
        steps,
        runbook.createdAt(),
        runbook.updatedAt());
  }

  private void validateCreateRunbookRequest(CreateRunbookRequest request) {
    if (request == null || request.name() == null || request.name().isBlank()) {
      throw new AppException("RUNBOOK_NAME_REQUIRED", "Runbook name is required");
    }
  }

  private String normalizedRisk(String riskLevel) {
    String value = blankToDefault(riskLevel, "medium");
    return switch (value) {
      case "low", "medium", "high", "critical" -> value;
      default -> "medium";
    };
  }

  private String normalizedActionType(String actionType) {
    String value = blankToDefault(actionType, "manual");
    return switch (value) {
      case "manual", "shell", "ansible", "http" -> value;
      default -> "manual";
    };
  }

  private String normalizedTargetType(String targetType) {
    String value = blankToDefault(targetType, "human");
    return switch (value) {
      case "human", "host", "service", "cluster" -> value;
      default -> "human";
    };
  }

  private String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
