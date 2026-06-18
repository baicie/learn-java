package io.aegisops.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.ExecutionJson;
import io.aegisops.execution.ExecutionProperties;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionRunStatusUpdateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.execution.dto.ExecutionStepStatusUpdateCommand;
import io.aegisops.runner.executor.ManualStepExecutor;
import io.aegisops.runner.executor.ShellDryRunStepExecutor;
import io.aegisops.runner.executor.UnsupportedStepExecutor;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RunnerExecutionServiceTest {
  @Test
  void processManualAndShellDryRunStepsSuccessfully() {
    FakeRunnerRepository repository = new FakeRunnerRepository();
    repository.claimed = run("exec_1", "dry_run");
    repository.steps.add(step("step_1", 1, "manual", "{}"));
    repository.steps.add(step("step_2", 2, "shell", "{\"command\":\"systemctl status app\"}"));

    ExecutionProperties executionProperties = new ExecutionProperties();
    RunnerProperties runnerProperties = new RunnerProperties();
    runnerProperties.setRunnerId("runner_1");

    RunnerExecutionService service =
        new RunnerExecutionService(
            repository,
            executionProperties,
            runnerProperties,
            List.of(
                new ManualStepExecutor(),
                new ShellDryRunStepExecutor(new ObjectMapper()),
                new UnsupportedStepExecutor()));

    boolean processed = service.processNext();

    assertTrue(processed);
    assertEquals("succeeded", repository.runStatus);
    assertEquals("succeeded", repository.planStatus);
    assertEquals(List.of("running", "succeeded", "running", "succeeded"), repository.stepStatuses);
  }

  @Test
  void unsupportedStepFailsRunAndSkipsRemaining() {
    FakeRunnerRepository repository = new FakeRunnerRepository();
    repository.claimed = run("exec_1", "dry_run");
    repository.steps.add(step("step_1", 1, "http", "{}"));
    repository.steps.add(step("step_2", 2, "manual", "{}"));

    ExecutionProperties executionProperties = new ExecutionProperties();
    RunnerProperties runnerProperties = new RunnerProperties();
    runnerProperties.setRunnerId("runner_1");

    RunnerExecutionService service =
        new RunnerExecutionService(
            repository,
            executionProperties,
            runnerProperties,
            List.of(new ManualStepExecutor(), new UnsupportedStepExecutor()));

    service.processNext();

    assertEquals("failed", repository.runStatus);
    assertEquals("failed", repository.planStatus);
    assertTrue(repository.stepStatuses.contains("failed"));
    assertTrue(repository.stepStatuses.contains("skipped"));
  }

  @Test
  void emptyStepsFailRunAndPlan() {
    FakeRunnerRepository repository = new FakeRunnerRepository();
    repository.claimed = run("exec_1", "dry_run");

    ExecutionProperties executionProperties = new ExecutionProperties();
    RunnerProperties runnerProperties = new RunnerProperties();
    runnerProperties.setRunnerId("runner_1");

    RunnerExecutionService service =
        new RunnerExecutionService(
            repository,
            executionProperties,
            runnerProperties,
            List.of(new ManualStepExecutor(), new UnsupportedStepExecutor()));

    service.processNext();

    assertEquals("failed", repository.runStatus);
    assertEquals("failed", repository.planStatus);
  }

  @Test
  void processFailsWhenPlanStatusUpdateFails() {
    FakeRunnerRepository repository = new FakeRunnerRepository();
    repository.claimed = run("exec_1", "dry_run");
    repository.failPlanStatusUpdate = true;
    repository.steps.add(step("step_1", 1, "manual", "{}"));

    ExecutionProperties executionProperties = new ExecutionProperties();
    RunnerProperties runnerProperties = new RunnerProperties();
    runnerProperties.setRunnerId("runner_1");

    RunnerExecutionService service =
        new RunnerExecutionService(
            repository,
            executionProperties,
            runnerProperties,
            List.of(new ManualStepExecutor(), new UnsupportedStepExecutor()));

    assertThrows(AppException.class, service::processNext);
  }

  private ExecutionRunRecord run(String id, String mode) {
    return new ExecutionRunRecord(
        id,
        "tenant_1",
        "inc_1",
        "plan_1",
        "running",
        mode,
        "alice",
        "runner_1",
        OffsetDateTime.now(),
        null,
        null,
        null,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private ExecutionStepRecord step(String id, int sequence, String actionType, String payload) {
    ExecutionJson json = new ExecutionJson(new ObjectMapper());
    return new ExecutionStepRecord(
        id,
        "tenant_1",
        "exec_1",
        "planstep_" + sequence,
        sequence,
        "step " + sequence,
        actionType,
        "human",
        "queued",
        payload == null ? json.write(java.util.Map.of()) : payload,
        "",
        null,
        null,
        null,
        null,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private static class FakeRunnerRepository extends RunnerFakeExecutionRepositoryBase {
    ExecutionRunRecord claimed;
    final List<ExecutionStepRecord> steps = new ArrayList<>();
    final List<String> stepStatuses = new ArrayList<>();
    String runStatus;
    String planStatus;
    boolean failPlanStatusUpdate;

    @Override
    public Optional<ExecutionRunRecord> claimNextQueuedRun(String runnerId) {
      return Optional.ofNullable(claimed);
    }

    @Override
    public List<ExecutionStepRecord> listExecutionSteps(String tenantId, String executionId) {
      return steps;
    }

    @Override
    public boolean updateStepStatus(ExecutionStepStatusUpdateCommand command) {
      stepStatuses.add(command.status());
      return true;
    }

    @Override
    public boolean updateRunStatus(ExecutionRunStatusUpdateCommand command) {
      runStatus = command.status();
      return true;
    }

    @Override
    public boolean updatePlanStatus(String tenantId, String planId, String status) {
      if (failPlanStatusUpdate) {
        return false;
      }
      planStatus = status;
      return true;
    }
  }
}
