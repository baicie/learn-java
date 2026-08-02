package io.aegisops.runner;

import io.aegisops.common.exception.AppException;
import io.aegisops.common.security.InvalidExecutionGrantException;
import io.aegisops.execution.ExecutionProperties;
import io.aegisops.execution.dto.ExecutionArtifactCreateCommand;
import io.aegisops.execution.dto.ExecutionAuditEventCreateCommand;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionRunStatusUpdateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.execution.dto.ExecutionStepStatusUpdateCommand;
import io.aegisops.execution.service.ExecutionApplicationService;
import io.aegisops.execution.service.RollbackApplicationService;
import io.aegisops.runner.executor.StepExecutionContext;
import io.aegisops.runner.executor.StepExecutionResult;
import io.aegisops.runner.executor.StepExecutor;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Picks up queued execution runs and drives each step through its {@link StepExecutor}. This class
 * sits in {@code apps/aiops-runner} and must only depend on cross-module {@code
 * *ApplicationService} contracts (e.g. {@link ExecutionApplicationService}) — it MUST NOT touch the
 * {@code io.aegisops.execution.*Repository} types directly. The ArchUnit guard in {@code
 * apps/aiops-runner} enforces that.
 */
@Service
public class RunnerExecutionService {
  private final ExecutionApplicationService executionApplicationService;
  private final RollbackApplicationService rollbackApplicationService;
  private final ExecutionProperties executionProperties;
  private final RunnerProperties runnerProperties;
  private final List<StepExecutor> executors;
  private final ExecutionGrantValidator executionGrantValidator;

  public RunnerExecutionService(
      ExecutionApplicationService executionApplicationService,
      RollbackApplicationService rollbackApplicationService,
      ExecutionProperties executionProperties,
      RunnerProperties runnerProperties,
      List<StepExecutor> executors,
      ExecutionGrantValidator executionGrantValidator) {
    this.executionApplicationService = executionApplicationService;
    this.rollbackApplicationService = rollbackApplicationService;
    this.executionProperties = executionProperties;
    this.runnerProperties = runnerProperties;
    this.executors =
        executors.stream()
            .sorted(Comparator.comparing(executor -> executor.getClass().getSimpleName()))
            .toList();
    this.executionGrantValidator = executionGrantValidator;
  }

  @Transactional
  public boolean processNext() {
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime leaseUntil = now.plusSeconds(executionProperties.normalizedLeaseSeconds());

    Optional<ExecutionRunRecord> claimed =
        executionApplicationService.claimNextQueuedRun(
            runnerProperties.getRunnerId(), now, leaseUntil);

    if (claimed.isEmpty()) {
      return false;
    }

    process(claimed.get());
    return true;
  }

  public int sweepTimeouts() {
    List<ExecutionRunRecord> expired =
        executionApplicationService.findExpiredRunningRuns(
            OffsetDateTime.now(), Math.max(1, runnerProperties.getTimeoutSweepLimit()));

    int count = 0;
    for (ExecutionRunRecord run : expired) {
      timeout(run);
      count++;
    }
    return count;
  }

  public void process(ExecutionRunRecord run) {
    List<ExecutionStepRecord> steps =
        executionApplicationService.listExecutionSteps(run.tenantId(), run.id());

    try {
      executionGrantValidator.validate(run, steps);
    } catch (InvalidExecutionGrantException exception) {
      rejectInvalidGrant(run);
      return;
    }

    if (steps.isEmpty()) {
      failRunAndPlan(run, "Execution has no steps.");
      return;
    }

    boolean failed = false;
    String errorMessage = null;

    for (ExecutionStepRecord step : steps) {
      if (!"queued".equals(step.status())) {
        continue;
      }

      heartbeat(run);

      StepExecutionResult result = executeStep(run, step);
      persistArtifacts(result.artifacts());

      heartbeat(run);

      if (!result.success()) {
        failed = true;
        errorMessage = result.errorMessage();
        skipRemainingQueuedSteps(run, steps, step.sequenceNo());
        break;
      }
    }

    if (failed) {
      failRunAndPlan(run, errorMessage);
    } else {
      succeedRunAndPlan(run);
    }
  }

  private StepExecutionResult executeStep(ExecutionRunRecord run, ExecutionStepRecord step) {
    OffsetDateTime startedAt = OffsetDateTime.now();

    ensureUpdated(
        executionApplicationService.updateStepStatus(
            new ExecutionStepStatusUpdateCommand(
                run.tenantId(), step.id(), "running", startedAt, null, null, null)),
        "EXECUTION_STEP_UPDATE_FAILED",
        "Execution step was not marked running");

    StepExecutor executor =
        executors.stream()
            .filter(item -> item.supports(step.actionType()))
            .findFirst()
            .orElseThrow(() -> new AppException("EXECUTOR_NOT_FOUND", "Executor not found"));

    StepExecutionResult result =
        executor.execute(new StepExecutionContext(run, executionProperties.isLiveEnabled()), step);

    OffsetDateTime finishedAt = OffsetDateTime.now();

    ensureUpdated(
        executionApplicationService.updateStepStatus(
            new ExecutionStepStatusUpdateCommand(
                run.tenantId(),
                step.id(),
                result.success() ? "succeeded" : "failed",
                startedAt,
                finishedAt,
                result.output(),
                result.errorMessage())),
        "EXECUTION_STEP_UPDATE_FAILED",
        "Execution step final status was not updated");

    return result;
  }

  private void persistArtifacts(List<ExecutionArtifactCreateCommand> artifacts) {
    for (ExecutionArtifactCreateCommand artifact : artifacts) {
      executionApplicationService.createArtifact(artifact);
      if (artifact.stepId() != null && !artifact.stepId().isBlank()) {
        ensureUpdated(
            executionApplicationService.incrementStepArtifactCount(
                artifact.tenantId(), artifact.stepId()),
            "EXECUTION_ARTIFACT_COUNT_UPDATE_FAILED",
            "Execution step artifact count was not updated");
      }
    }
  }

  private void skipRemainingQueuedSteps(
      ExecutionRunRecord run, List<ExecutionStepRecord> steps, int failedSequenceNo) {
    for (ExecutionStepRecord step : steps) {
      if (step.sequenceNo() > failedSequenceNo && "queued".equals(step.status())) {
        ensureUpdated(
            executionApplicationService.updateStepStatus(
                new ExecutionStepStatusUpdateCommand(
                    run.tenantId(),
                    step.id(),
                    "skipped",
                    null,
                    OffsetDateTime.now(),
                    null,
                    "Skipped because previous step failed.")),
            "EXECUTION_STEP_UPDATE_FAILED",
            "Execution step was not marked skipped");
      }
    }
  }

  private void heartbeat(ExecutionRunRecord run) {
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime leaseUntil = now.plusSeconds(executionProperties.normalizedLeaseSeconds());

    ensureUpdated(
        executionApplicationService.heartbeat(
            run.tenantId(), run.id(), runnerProperties.getRunnerId(), now, leaseUntil),
        "EXECUTION_HEARTBEAT_FAILED",
        "Execution heartbeat failed");
  }

  private void timeout(ExecutionRunRecord run) {
    ensureUpdated(
        executionApplicationService.timeoutRun(
            run.tenantId(), run.id(), "Execution lease timed out."),
        "EXECUTION_TIMEOUT_FAILED",
        "Execution run was not marked timeout");

    ensureUpdated(
        executionApplicationService.timeoutExecutionSteps(run.tenantId(), run.id()),
        "EXECUTION_STEP_TIMEOUT_FAILED",
        "Execution steps were not marked timeout");

    if ("rollback".equals(run.executionKind()) && run.rollbackPlanId() != null) {
      ensureUpdated(
          rollbackApplicationService.markFailed(run.tenantId(), run.rollbackPlanId()),
          "ROLLBACK_PLAN_UPDATE_FAILED",
          "Rollback plan status was not updated");
      return;
    }

    ensureUpdated(
        executionApplicationService.updatePlanStatus(run.tenantId(), run.planId(), "failed"),
        "AUTOMATION_PLAN_UPDATE_FAILED",
        "Automation plan status was not updated");
  }

  private void succeedRunAndPlan(ExecutionRunRecord run) {
    ensureUpdated(
        executionApplicationService.updateRunStatus(
            new ExecutionRunStatusUpdateCommand(
                run.tenantId(),
                run.id(),
                "succeeded",
                runnerProperties.getRunnerId(),
                run.startedAt(),
                OffsetDateTime.now(),
                null,
                "Execution completed successfully.")),
        "EXECUTION_RUN_UPDATE_FAILED",
        "Execution run was not marked succeeded");

    if ("rollback".equals(run.executionKind()) && run.rollbackPlanId() != null) {
      rollbackApplicationService.markSucceeded(run.tenantId(), run.rollbackPlanId());
      return;
    }

    ensureUpdated(
        executionApplicationService.updatePlanStatus(run.tenantId(), run.planId(), "succeeded"),
        "AUTOMATION_PLAN_UPDATE_FAILED",
        "Automation plan status was not updated");
  }

  private void failRunAndPlan(ExecutionRunRecord run, String errorMessage) {
    ensureUpdated(
        executionApplicationService.updateRunStatus(
            new ExecutionRunStatusUpdateCommand(
                run.tenantId(),
                run.id(),
                "failed",
                runnerProperties.getRunnerId(),
                run.startedAt(),
                OffsetDateTime.now(),
                errorMessage,
                "Execution failed.")),
        "EXECUTION_RUN_UPDATE_FAILED",
        "Execution run was not marked failed");

    if ("rollback".equals(run.executionKind()) && run.rollbackPlanId() != null) {
      rollbackApplicationService.markFailed(run.tenantId(), run.rollbackPlanId());
      return;
    }

    ensureUpdated(
        executionApplicationService.updatePlanStatus(run.tenantId(), run.planId(), "failed"),
        "AUTOMATION_PLAN_UPDATE_FAILED",
        "Automation plan status was not updated");
  }

  private void rejectInvalidGrant(ExecutionRunRecord run) {
    failRunAndPlan(run, "Execution grant validation failed.");
    executionApplicationService.appendAuditEvent(
        new ExecutionAuditEventCreateCommand(
            "execaudit_" + UUID.randomUUID().toString().replace("-", ""),
            run.tenantId(),
            run.id(),
            null,
            "execution_grant_rejected",
            "aiops-runner",
            "Execution rejected before executor invocation.",
            "{\"reason\":\"grant_validation_failed\"}"));
  }

  private void ensureUpdated(boolean updated, String code, String message) {
    if (!updated) {
      throw new AppException(code, message);
    }
  }
}
