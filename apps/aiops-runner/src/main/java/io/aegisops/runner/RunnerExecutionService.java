package io.aegisops.runner;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.ExecutionProperties;
import io.aegisops.execution.ExecutionRepository;
import io.aegisops.execution.RollbackRepository;
import io.aegisops.execution.dto.ExecutionArtifactCreateCommand;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionRunStatusUpdateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.execution.dto.ExecutionStepStatusUpdateCommand;
import io.aegisops.runner.executor.StepExecutionContext;
import io.aegisops.runner.executor.StepExecutionResult;
import io.aegisops.runner.executor.StepExecutor;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunnerExecutionService {
  private final ExecutionRepository repository;
  private final RollbackRepository rollbackRepository;
  private final ExecutionProperties executionProperties;
  private final RunnerProperties runnerProperties;
  private final List<StepExecutor> executors;

  public RunnerExecutionService(
      ExecutionRepository repository,
      RollbackRepository rollbackRepository,
      ExecutionProperties executionProperties,
      RunnerProperties runnerProperties,
      List<StepExecutor> executors) {
    this.repository = repository;
    this.rollbackRepository = rollbackRepository;
    this.executionProperties = executionProperties;
    this.runnerProperties = runnerProperties;
    this.executors =
        executors.stream()
            .sorted(Comparator.comparing(executor -> executor.getClass().getSimpleName()))
            .toList();
  }

  @Transactional
  public boolean processNext() {
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime leaseUntil = now.plusSeconds(executionProperties.normalizedLeaseSeconds());

    Optional<ExecutionRunRecord> claimed =
        repository.claimNextQueuedRun(runnerProperties.getRunnerId(), now, leaseUntil);

    if (claimed.isEmpty()) {
      return false;
    }

    process(claimed.get());
    return true;
  }

  public int sweepTimeouts() {
    List<ExecutionRunRecord> expired =
        repository.findExpiredRunningRuns(
            OffsetDateTime.now(), Math.max(1, runnerProperties.getTimeoutSweepLimit()));

    int count = 0;
    for (ExecutionRunRecord run : expired) {
      timeout(run);
      count++;
    }
    return count;
  }

  public void process(ExecutionRunRecord run) {
    List<ExecutionStepRecord> steps = repository.listExecutionSteps(run.tenantId(), run.id());

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
        repository.updateStepStatus(
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
        repository.updateStepStatus(
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
      repository.createArtifact(artifact);
      if (artifact.stepId() != null && !artifact.stepId().isBlank()) {
        ensureUpdated(
            repository.incrementStepArtifactCount(artifact.tenantId(), artifact.stepId()),
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
            repository.updateStepStatus(
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
        repository.heartbeat(
            run.tenantId(), run.id(), runnerProperties.getRunnerId(), now, leaseUntil),
        "EXECUTION_HEARTBEAT_FAILED",
        "Execution heartbeat failed");
  }

  private void timeout(ExecutionRunRecord run) {
    ensureUpdated(
        repository.timeoutRun(run.tenantId(), run.id(), "Execution lease timed out."),
        "EXECUTION_TIMEOUT_FAILED",
        "Execution run was not marked timeout");

    ensureUpdated(
        repository.timeoutExecutionSteps(run.tenantId(), run.id()),
        "EXECUTION_STEP_TIMEOUT_FAILED",
        "Execution steps were not marked timeout");

    ensureUpdated(
        repository.updatePlanStatus(run.tenantId(), run.planId(), "failed"),
        "AUTOMATION_PLAN_UPDATE_FAILED",
        "Automation plan status was not updated");
  }

  private void succeedRunAndPlan(ExecutionRunRecord run) {
    ensureUpdated(
        repository.updateRunStatus(
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
      rollbackRepository.markSucceeded(run.tenantId(), run.rollbackPlanId());
      return;
    }

    ensureUpdated(
        repository.updatePlanStatus(run.tenantId(), run.planId(), "succeeded"),
        "AUTOMATION_PLAN_UPDATE_FAILED",
        "Automation plan status was not updated");
  }

  private void failRunAndPlan(ExecutionRunRecord run, String errorMessage) {
    ensureUpdated(
        repository.updateRunStatus(
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
      rollbackRepository.markFailed(run.tenantId(), run.rollbackPlanId());
      return;
    }

    ensureUpdated(
        repository.updatePlanStatus(run.tenantId(), run.planId(), "failed"),
        "AUTOMATION_PLAN_UPDATE_FAILED",
        "Automation plan status was not updated");
  }

  private void ensureUpdated(boolean updated, String code, String message) {
    if (!updated) {
      throw new AppException(code, message);
    }
  }
}
