package io.aegisops.runner;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.ExecutionProperties;
import io.aegisops.execution.ExecutionRepository;
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
  private final ExecutionProperties executionProperties;
  private final RunnerProperties runnerProperties;
  private final List<StepExecutor> executors;

  public RunnerExecutionService(
      ExecutionRepository repository,
      ExecutionProperties executionProperties,
      RunnerProperties runnerProperties,
      List<StepExecutor> executors) {
    this.repository = repository;
    this.executionProperties = executionProperties;
    this.runnerProperties = runnerProperties;
    this.executors =
        executors.stream()
            .sorted(Comparator.comparing(executor -> executor.getClass().getSimpleName()))
            .toList();
  }

  @Transactional
  public boolean processNext() {
    Optional<ExecutionRunRecord> claimed =
        repository.claimNextQueuedRun(runnerProperties.getRunnerId());

    if (claimed.isEmpty()) {
      return false;
    }

    process(claimed.get());
    return true;
  }

  public void process(ExecutionRunRecord run) {
    List<ExecutionStepRecord> steps = repository.listExecutionSteps(run.tenantId(), run.id());

    if (steps.isEmpty()) {
      failRun(run, "Execution has no steps.");
      return;
    }

    boolean failed = false;
    String errorMessage = null;

    for (ExecutionStepRecord step : steps) {
      if (!"queued".equals(step.status())) {
        continue;
      }

      StepExecutionResult result = executeStep(run, step);
      if (!result.success()) {
        failed = true;
        errorMessage = result.errorMessage();
        skipRemainingQueuedSteps(run, steps, step.sequenceNo());
        break;
      }
    }

    if (failed) {
      failRun(run, errorMessage);
      repository.updatePlanStatus(run.tenantId(), run.planId(), "failed");
    } else {
      succeedRun(run);
      repository.updatePlanStatus(run.tenantId(), run.planId(), "succeeded");
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

  private void skipRemainingQueuedSteps(
      ExecutionRunRecord run, List<ExecutionStepRecord> steps, int failedSequenceNo) {
    for (ExecutionStepRecord step : steps) {
      if (step.sequenceNo() > failedSequenceNo && "queued".equals(step.status())) {
        repository.updateStepStatus(
            new ExecutionStepStatusUpdateCommand(
                run.tenantId(),
                step.id(),
                "skipped",
                null,
                OffsetDateTime.now(),
                null,
                "Skipped because previous step failed."));
      }
    }
  }

  private void succeedRun(ExecutionRunRecord run) {
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
  }

  private void failRun(ExecutionRunRecord run, String errorMessage) {
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
  }

  private void ensureUpdated(boolean updated, String code, String message) {
    if (!updated) {
      throw new AppException(code, message);
    }
  }
}
