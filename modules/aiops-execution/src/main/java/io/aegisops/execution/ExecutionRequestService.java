package io.aegisops.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.ExecutionCreateRequest;
import io.aegisops.execution.dto.ExecutionRunCreateCommand;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionRunResponse;
import io.aegisops.execution.dto.ExecutionStepCreateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.execution.dto.ExecutionStepResponse;
import io.aegisops.execution.dto.PlanForExecutionRecord;
import io.aegisops.execution.dto.PlanStepForExecutionRecord;
import io.aegisops.execution.dto.TimelineCreateCommand;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionRequestService {
  private final ExecutionRepository repository;
  private final ExecutionProperties properties;
  private final ExecutionJson json;

  public ExecutionRequestService(
      ExecutionRepository repository, ExecutionProperties properties, ObjectMapper objectMapper) {
    this.repository = repository;
    this.properties = properties;
    this.json = new ExecutionJson(objectMapper);
  }

  @Transactional
  public ExecutionRunResponse createExecution(
      String tenantId, String planId, ExecutionCreateRequest request) {
    ExecutionCreateRequest normalized =
        request == null ? new ExecutionCreateRequest(true, "system") : request;

    PlanForExecutionRecord plan =
        repository
            .findPlan(tenantId, planId)
            .orElseThrow(
                () -> new AppException("AUTOMATION_PLAN_NOT_FOUND", "Automation plan not found"));

    if (!"approved".equals(plan.status())) {
      throw new AppException(
          "AUTOMATION_PLAN_NOT_APPROVED", "Only approved automation plan can be executed");
    }

    if (!normalized.dryRunEnabled() && !properties.isLiveEnabled()) {
      throw new AppException(
          "LIVE_EXECUTION_DISABLED", "Live execution is disabled by configuration");
    }

    var active = repository.findLatestRunByPlan(tenantId, planId);
    if (active.isPresent() && List.of("queued", "running").contains(active.get().status())) {
      return toResponse(active.get(), repository.listExecutionSteps(tenantId, active.get().id()));
    }

    List<PlanStepForExecutionRecord> planSteps = repository.listPlanSteps(planId);
    if (planSteps.isEmpty()) {
      throw new AppException("AUTOMATION_PLAN_STEP_EMPTY", "Automation plan has no steps");
    }

    String executionId = newId("exec");
    String mode = normalized.dryRunEnabled() ? "dry_run" : "live";
    String requestedBy = blankToDefault(normalized.requestedBy(), "system");

    repository.createRun(
        new ExecutionRunCreateCommand(
            executionId, tenantId, plan.incidentId(), plan.id(), "queued", mode, requestedBy));

    repository.createSteps(
        planSteps.stream().map(step -> toExecutionStep(tenantId, executionId, step)).toList());

    ensureUpdated(
        repository.updatePlanStatus(tenantId, planId, "executing"),
        "AUTOMATION_PLAN_UPDATE_FAILED",
        "Automation plan status was not updated");

    repository.addTimeline(
        timeline(
            plan.incidentId(),
            "execution_queued",
            "Execution queued",
            "Automation execution was queued for runner.",
            Map.of(
                "executionId", executionId,
                "planId", planId,
                "mode", mode,
                "requestedBy", requestedBy)));

    ExecutionRunRecord saved =
        repository
            .findRun(tenantId, executionId)
            .orElseThrow(() -> new AppException("EXECUTION_NOT_FOUND", "Execution not found"));

    return toResponse(saved, repository.listExecutionSteps(tenantId, executionId));
  }

  public ExecutionRunResponse getExecution(String tenantId, String executionId) {
    ExecutionRunRecord run =
        repository
            .findRun(tenantId, executionId)
            .orElseThrow(() -> new AppException("EXECUTION_NOT_FOUND", "Execution not found"));

    return toResponse(run, repository.listExecutionSteps(tenantId, executionId));
  }

  public ExecutionRunResponse latestByPlan(String tenantId, String planId) {
    ExecutionRunRecord run =
        repository
            .findLatestRunByPlan(tenantId, planId)
            .orElseThrow(() -> new AppException("EXECUTION_NOT_FOUND", "Execution not found"));

    return toResponse(run, repository.listExecutionSteps(tenantId, run.id()));
  }

  @Transactional
  public ExecutionRunResponse cancel(String tenantId, String executionId) {
    ExecutionRunRecord run =
        repository
            .findRun(tenantId, executionId)
            .orElseThrow(() -> new AppException("EXECUTION_NOT_FOUND", "Execution not found"));

    if (!List.of("queued", "running").contains(run.status())) {
      throw new AppException(
          "EXECUTION_STATUS_INVALID", "Only queued or running execution can be cancelled");
    }

    ensureUpdated(
        repository.cancelRun(tenantId, executionId),
        "EXECUTION_CANCEL_FAILED",
        "Execution was not cancelled");

    ensureUpdated(
        repository.cancelExecutionSteps(tenantId, executionId),
        "EXECUTION_STEP_CANCEL_FAILED",
        "Execution steps were not cancelled");

    ensureUpdated(
        repository.updatePlanStatus(tenantId, run.planId(), "cancelled"),
        "AUTOMATION_PLAN_UPDATE_FAILED",
        "Automation plan status was not updated");

    repository.addTimeline(
        timeline(
            run.incidentId(),
            "execution_cancelled",
            "Execution cancelled",
            "Automation execution was cancelled.",
            Map.of("executionId", executionId, "planId", run.planId())));

    return getExecution(tenantId, executionId);
  }

  private ExecutionStepCreateCommand toExecutionStep(
      String tenantId, String executionId, PlanStepForExecutionRecord step) {
    String command = json.textValue(step.actionPayloadJson(), "command");

    return new ExecutionStepCreateCommand(
        newId("execstep"),
        tenantId,
        executionId,
        step.id(),
        step.sequenceNo(),
        step.name(),
        step.actionType(),
        step.targetType(),
        "queued",
        step.actionPayloadJson(),
        command);
  }

  private ExecutionRunResponse toResponse(ExecutionRunRecord run, List<ExecutionStepRecord> steps) {
    return new ExecutionRunResponse(
        run.id(),
        run.tenantId(),
        run.incidentId(),
        run.planId(),
        run.status(),
        run.mode(),
        run.requestedBy(),
        run.runnerId(),
        run.errorMessage(),
        run.summary(),
        steps.stream().map(this::toStepResponse).toList(),
        run.startedAt(),
        run.finishedAt(),
        run.createdAt(),
        run.updatedAt());
  }

  private ExecutionStepResponse toStepResponse(ExecutionStepRecord step) {
    return new ExecutionStepResponse(
        step.id(),
        step.executionId(),
        step.planStepId(),
        step.sequenceNo(),
        step.name(),
        step.actionType(),
        step.targetType(),
        step.status(),
        step.output(),
        step.errorMessage(),
        step.startedAt(),
        step.finishedAt(),
        step.createdAt(),
        step.updatedAt());
  }

  private TimelineCreateCommand timeline(
      String incidentId,
      String eventType,
      String title,
      String description,
      Map<String, Object> payload) {
    return new TimelineCreateCommand(
        newId("tl"),
        incidentId,
        OffsetDateTime.now(),
        eventType,
        title,
        description,
        "system",
        json.write(payload));
  }

  private void ensureUpdated(boolean updated, String code, String message) {
    if (!updated) {
      throw new AppException(code, message);
    }
  }

  private String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
