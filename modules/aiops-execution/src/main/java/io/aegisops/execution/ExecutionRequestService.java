package io.aegisops.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.ExecutionApprovalSnapshotRecord;
import io.aegisops.execution.dto.ExecutionArtifactRecord;
import io.aegisops.execution.dto.ExecutionArtifactResponse;
import io.aegisops.execution.dto.ExecutionCreateRequest;
import io.aegisops.execution.dto.ExecutionRetryRequest;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionRequestService {
  private final ExecutionRepository repository;
  private final RollbackRepository rollbackRepository;
  private final ExecutionProperties properties;
  private final ExecutionJson json;

  public ExecutionRequestService(
      ExecutionRepository repository, ExecutionProperties properties, ObjectMapper objectMapper) {
    this(repository, null, properties, objectMapper);
  }

  @Autowired
  public ExecutionRequestService(
      ExecutionRepository repository,
      RollbackRepository rollbackRepository,
      ExecutionProperties properties,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.rollbackRepository = rollbackRepository;
    this.properties = properties;
    this.json = new ExecutionJson(objectMapper);
  }

  @Transactional
  public ExecutionRunResponse createExecution(
      String tenantId, String planId, ExecutionCreateRequest request) {
    ExecutionCreateRequest normalized =
        request == null ? new ExecutionCreateRequest(true, "system", 1) : request;

    PlanForExecutionRecord plan = loadPlan(tenantId, planId);

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
      return toResponse(
          active.get(),
          repository.listExecutionSteps(tenantId, active.get().id()),
          repository.listArtifacts(tenantId, active.get().id()));
    }

    ExecutionApprovalSnapshotRecord approvalSnapshot = null;
    if (!normalized.dryRunEnabled()) {
      approvalSnapshot =
          repository
              .findLatestApprovedApprovalSnapshot(tenantId, planId)
              .orElseThrow(
                  () ->
                      new AppException(
                          "EXECUTION_APPROVAL_REQUIRED",
                          "Live execution requires an approved automation approval"));
    }

    int maxAttempts =
        Math.min(normalized.normalizedMaxAttempts(), properties.normalizedMaxRetryAttempts());

    return createQueuedRun(
        tenantId,
        new QueuedRunContext(
            plan,
            normalized.dryRunEnabled() ? "dry_run" : "live",
            blankToDefault(normalized.requestedBy(), "system"),
            1,
            maxAttempts,
            null,
            approvalSnapshot,
            "execution_queued",
            "Execution queued",
            "Automation execution was queued for runner."));
  }

  @Transactional
  public ExecutionRunResponse retry(
      String tenantId, String executionId, ExecutionRetryRequest request) {
    ExecutionRunRecord previous =
        repository
            .findRun(tenantId, executionId)
            .orElseThrow(() -> new AppException("EXECUTION_NOT_FOUND", "Execution not found"));

    if ("rollback".equals(previous.executionKind())) {
      throw new AppException(
          "ROLLBACK_EXECUTION_RETRY_UNSUPPORTED",
          "Rollback execution retry is not supported by normal execution retry API");
    }

    if (!List.of("failed", "timeout").contains(previous.status())) {
      throw new AppException(
          "EXECUTION_RETRY_STATUS_INVALID", "Only failed or timeout execution can be retried");
    }

    if (previous.attempt() >= previous.maxAttempts()) {
      throw new AppException("EXECUTION_RETRY_EXHAUSTED", "Execution retry attempts exhausted");
    }

    ExecutionRunRecord latest =
        repository
            .findLatestRunByPlan(tenantId, previous.planId())
            .orElseThrow(() -> new AppException("EXECUTION_NOT_FOUND", "Execution not found"));

    if (!latest.id().equals(previous.id())) {
      if (List.of("queued", "running").contains(latest.status())) {
        return toResponse(
            latest,
            repository.listExecutionSteps(tenantId, latest.id()),
            repository.listArtifacts(tenantId, latest.id()));
      }

      throw new AppException(
          "EXECUTION_RETRY_NOT_LATEST", "Only latest failed or timeout execution can be retried");
    }

    PlanForExecutionRecord plan = loadPlan(tenantId, previous.planId());

    if (!"failed".equals(plan.status())) {
      throw new AppException(
          "AUTOMATION_PLAN_RETRY_STATUS_INVALID", "Only failed automation plan can be retried");
    }

    ExecutionApprovalSnapshotRecord approvalSnapshot = null;
    if ("live".equals(previous.mode())) {
      approvalSnapshot =
          repository
              .findLatestApprovedApprovalSnapshot(tenantId, previous.planId())
              .orElseThrow(
                  () ->
                      new AppException(
                          "EXECUTION_APPROVAL_REQUIRED",
                          "Live execution retry requires an approved automation approval"));
    }

    return createQueuedRun(
        tenantId,
        new QueuedRunContext(
            plan,
            previous.mode(),
            blankToDefault(request == null ? null : request.requestedBy(), previous.requestedBy()),
            previous.attempt() + 1,
            previous.maxAttempts(),
            previous.id(),
            approvalSnapshot,
            "execution_retried",
            "Execution retried",
            "Automation execution retry was queued."));
  }

  public ExecutionRunResponse getExecution(String tenantId, String executionId) {
    ExecutionRunRecord run =
        repository
            .findRun(tenantId, executionId)
            .orElseThrow(() -> new AppException("EXECUTION_NOT_FOUND", "Execution not found"));

    return toResponse(
        run,
        repository.listExecutionSteps(tenantId, executionId),
        repository.listArtifacts(tenantId, executionId));
  }

  public ExecutionRunResponse latestByPlan(String tenantId, String planId) {
    ExecutionRunRecord run =
        repository
            .findLatestRunByPlan(tenantId, planId)
            .orElseThrow(() -> new AppException("EXECUTION_NOT_FOUND", "Execution not found"));

    return toResponse(
        run,
        repository.listExecutionSteps(tenantId, run.id()),
        repository.listArtifacts(tenantId, run.id()));
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

    if ("rollback".equals(run.executionKind())
        && run.rollbackPlanId() != null
        && rollbackRepository != null) {
      ensureUpdated(
          rollbackRepository.markCancelled(tenantId, run.rollbackPlanId()),
          "ROLLBACK_PLAN_UPDATE_FAILED",
          "Rollback plan status was not updated");
    } else {
      ensureUpdated(
          repository.updatePlanStatus(tenantId, run.planId(), "cancelled"),
          "AUTOMATION_PLAN_UPDATE_FAILED",
          "Automation plan status was not updated");
    }

    repository.addTimeline(
        timeline(
            run.incidentId(),
            "execution_cancelled",
            "Execution cancelled",
            "Automation execution was cancelled.",
            Map.of("executionId", executionId, "planId", run.planId())));

    return getExecution(tenantId, executionId);
  }

  private ExecutionRunResponse createQueuedRun(String tenantId, QueuedRunContext context) {
    PlanForExecutionRecord plan = context.plan();
    List<PlanStepForExecutionRecord> planSteps = repository.listPlanSteps(plan.id());
    if (planSteps.isEmpty()) {
      throw new AppException("AUTOMATION_PLAN_STEP_EMPTY", "Automation plan has no steps");
    }

    String executionId = newId("exec");

    repository.createRun(
        new ExecutionRunCreateCommand(
            executionId,
            tenantId,
            plan.incidentId(),
            plan.id(),
            "queued",
            context.mode(),
            context.requestedBy(),
            context.attempt(),
            context.maxAttempts(),
            context.retryOfExecutionId(),
            properties.normalizedRunTimeoutSeconds(),
            context.approvalSnapshot() == null ? null : context.approvalSnapshot().approvalId(),
            context.approvalSnapshot() == null ? "{}" : json.write(context.approvalSnapshot()),
            plan.riskLevel(),
            "normal",
            null,
            null));

    repository.createSteps(
        planSteps.stream()
            .map(step -> toExecutionStep(tenantId, executionId, step, context.attempt()))
            .toList());

    ensureUpdated(
        repository.updatePlanStatus(tenantId, plan.id(), "executing"),
        "AUTOMATION_PLAN_UPDATE_FAILED",
        "Automation plan status was not updated");

    repository.addTimeline(
        timeline(
            plan.incidentId(),
            context.eventType(),
            context.title(),
            context.description(),
            Map.of(
                "executionId", executionId,
                "planId", plan.id(),
                "mode", context.mode(),
                "requestedBy", context.requestedBy(),
                "attempt", context.attempt(),
                "maxAttempts", context.maxAttempts())));

    ExecutionRunRecord saved =
        repository
            .findRun(tenantId, executionId)
            .orElseThrow(() -> new AppException("EXECUTION_NOT_FOUND", "Execution not found"));

    return toResponse(
        saved,
        repository.listExecutionSteps(tenantId, executionId),
        repository.listArtifacts(tenantId, executionId));
  }

  private record QueuedRunContext(
      PlanForExecutionRecord plan,
      String mode,
      String requestedBy,
      int attempt,
      int maxAttempts,
      String retryOfExecutionId,
      ExecutionApprovalSnapshotRecord approvalSnapshot,
      String eventType,
      String title,
      String description) {}

  private ExecutionStepCreateCommand toExecutionStep(
      String tenantId, String executionId, PlanStepForExecutionRecord step, int attempt) {
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
        command,
        attempt,
        properties.normalizedStepTimeoutSeconds());
  }

  private PlanForExecutionRecord loadPlan(String tenantId, String planId) {
    return repository
        .findPlan(tenantId, planId)
        .orElseThrow(
            () -> new AppException("AUTOMATION_PLAN_NOT_FOUND", "Automation plan not found"));
  }

  private ExecutionRunResponse toResponse(
      ExecutionRunRecord run,
      List<ExecutionStepRecord> steps,
      List<ExecutionArtifactRecord> artifacts) {
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
        run.attempt(),
        run.maxAttempts(),
        run.retryOfExecutionId(),
        run.leaseUntil(),
        run.heartbeatAt(),
        run.timeoutSeconds(),
        run.approvalId(),
        run.approvalSnapshotJson(),
        run.planRiskLevel(),
        run.liveGuardPassedAt(),
        run.executionKind(),
        run.rollbackPlanId(),
        run.rollbackOfExecutionId(),
        steps.stream().map(step -> toStepResponse(step)).toList(),
        artifacts.stream().map(artifact -> toArtifactResponse(artifact)).toList(),
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
        step.attempt(),
        step.timeoutSeconds(),
        step.artifactCount(),
        step.startedAt(),
        step.finishedAt(),
        step.createdAt(),
        step.updatedAt());
  }

  private ExecutionArtifactResponse toArtifactResponse(ExecutionArtifactRecord artifact) {
    return new ExecutionArtifactResponse(
        artifact.id(),
        artifact.executionId(),
        artifact.stepId(),
        artifact.artifactType(),
        artifact.name(),
        artifact.content(),
        artifact.metadataJson(),
        artifact.createdAt());
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
