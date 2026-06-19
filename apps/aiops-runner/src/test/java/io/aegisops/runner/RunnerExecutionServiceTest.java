package io.aegisops.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.ExecutionProperties;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.runner.executor.ManualStepExecutor;
import io.aegisops.runner.executor.ShellDryRunStepExecutor;
import io.aegisops.runner.executor.UnsupportedStepExecutor;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunnerExecutionServiceTest {
  @Test
  void processRunWritesHeartbeatAndArtifacts() {
    FakeRunnerRepository repository = new FakeRunnerRepository();
    repository.claimed =
        run("exec_1", new RunState("dry_run", "running"), "normal", new RollbackRef(null, null));
    repository.steps.add(step("step_1", 1, "manual", "{}"));

    ExecutionProperties executionProperties = new ExecutionProperties();
    executionProperties.setLeaseSeconds(60);

    RunnerProperties runnerProperties = new RunnerProperties();
    runnerProperties.setRunnerId("runner_1");

    RunnerExecutionService service =
        new RunnerExecutionService(
            repository,
            repository,
            executionProperties,
            runnerProperties,
            List.of(
                new ManualStepExecutor(new ObjectMapper()),
                new ShellDryRunStepExecutor(new ObjectMapper()),
                new UnsupportedStepExecutor(new ObjectMapper())));

    boolean processed = service.processNext();

    assertTrue(processed);
    assertTrue(repository.heartbeatCount >= 2);
    assertEquals("succeeded", repository.runStatus);
    assertEquals("succeeded", repository.planStatus);
    assertEquals(1, repository.artifacts.size());
    assertEquals(1, repository.artifactIncrementCount);
  }

  @Test
  void timeoutSweepMarksRunStepsAndPlan() {
    FakeRunnerRepository repository = new FakeRunnerRepository();
    repository.expiredRuns.add(
        run("exec_1", new RunState("dry_run", "running"), "normal", new RollbackRef(null, null)));

    ExecutionProperties executionProperties = new ExecutionProperties();

    RunnerProperties runnerProperties = new RunnerProperties();
    runnerProperties.setRunnerId("runner_1");

    RunnerExecutionService service =
        new RunnerExecutionService(
            repository,
            repository,
            executionProperties,
            runnerProperties,
            List.of(new ManualStepExecutor(new ObjectMapper())));

    int count = service.sweepTimeouts();

    assertEquals(1, count);
    assertEquals("timeout", repository.timeoutRunStatus);
    assertEquals("failed", repository.planStatus);
    assertTrue(repository.stepsTimedOut);
  }

  @Test
  void unsupportedStepFailsRunAndSkipsRemaining() {
    FakeRunnerRepository repository = new FakeRunnerRepository();
    repository.claimed =
        run("exec_1", new RunState("dry_run", "running"), "normal", new RollbackRef(null, null));
    repository.steps.add(step("step_1", 1, "http", "{}"));
    repository.steps.add(step("step_2", 2, "manual", "{}"));

    ExecutionProperties executionProperties = new ExecutionProperties();
    RunnerProperties runnerProperties = new RunnerProperties();
    runnerProperties.setRunnerId("runner_1");

    RunnerExecutionService service =
        new RunnerExecutionService(
            repository,
            repository,
            executionProperties,
            runnerProperties,
            List.of(
                new ManualStepExecutor(new ObjectMapper()),
                new UnsupportedStepExecutor(new ObjectMapper())));

    service.processNext();

    assertEquals("failed", repository.runStatus);
    assertEquals("failed", repository.planStatus);
    assertTrue(repository.stepStatuses.contains("failed"));
    assertTrue(repository.stepStatuses.contains("skipped"));
  }

  @Test
  void emptyStepsFailRunAndPlan() {
    FakeRunnerRepository repository = new FakeRunnerRepository();
    repository.claimed =
        run("exec_1", new RunState("dry_run", "running"), "normal", new RollbackRef(null, null));

    ExecutionProperties executionProperties = new ExecutionProperties();
    RunnerProperties runnerProperties = new RunnerProperties();
    runnerProperties.setRunnerId("runner_1");

    RunnerExecutionService service =
        new RunnerExecutionService(
            repository,
            repository,
            executionProperties,
            runnerProperties,
            List.of(
                new ManualStepExecutor(new ObjectMapper()),
                new UnsupportedStepExecutor(new ObjectMapper())));

    service.processNext();

    assertEquals("failed", repository.runStatus);
    assertEquals("failed", repository.planStatus);
  }

  @Test
  void processFailsWhenPlanStatusUpdateFails() {
    FakeRunnerRepository repository = new FakeRunnerRepository();
    repository.claimed =
        run("exec_1", new RunState("dry_run", "running"), "normal", new RollbackRef(null, null));
    repository.failPlanStatusUpdate = true;
    repository.steps.add(step("step_1", 1, "manual", "{}"));

    ExecutionProperties executionProperties = new ExecutionProperties();
    RunnerProperties runnerProperties = new RunnerProperties();
    runnerProperties.setRunnerId("runner_1");

    RunnerExecutionService service =
        new RunnerExecutionService(
            repository,
            repository,
            executionProperties,
            runnerProperties,
            List.of(
                new ManualStepExecutor(new ObjectMapper()),
                new UnsupportedStepExecutor(new ObjectMapper())));

    assertThrows(AppException.class, service::processNext);
  }

  @Test
  void markRollbackPlanSucceededWhenRollbackRunSucceeded() {
    FakeRunnerRepository repository = new FakeRunnerRepository();
    repository.claimed =
        run(
            "exec_1",
            new RunState("running", "live"),
            "rollback",
            new RollbackRef("rbp_1", "exec_src"));
    repository.steps.add(step("step_1", 1, "manual", "{}"));

    ExecutionProperties executionProperties = new ExecutionProperties();
    RunnerProperties runnerProperties = new RunnerProperties();
    runnerProperties.setRunnerId("runner_1");

    RunnerExecutionService service =
        new RunnerExecutionService(
            repository,
            repository,
            executionProperties,
            runnerProperties,
            List.of(new ManualStepExecutor(new ObjectMapper())));

    service.processNext();

    assertEquals("succeeded", repository.runStatus);
    assertTrue(repository.rollbackSucceeded);
    assertEquals(null, repository.planStatus);
  }

  @Test
  void markRollbackPlanFailedWhenRollbackRunFailed() {
    FakeRunnerRepository repository = new FakeRunnerRepository();
    repository.claimed =
        run(
            "exec_1",
            new RunState("running", "live"),
            "rollback",
            new RollbackRef("rbp_1", "exec_src"));
    repository.steps.add(step("step_1", 1, "http", "{}"));

    ExecutionProperties executionProperties = new ExecutionProperties();
    RunnerProperties runnerProperties = new RunnerProperties();
    runnerProperties.setRunnerId("runner_1");

    RunnerExecutionService service =
        new RunnerExecutionService(
            repository,
            repository,
            executionProperties,
            runnerProperties,
            List.of(
                new ManualStepExecutor(new ObjectMapper()),
                new UnsupportedStepExecutor(new ObjectMapper())));

    service.processNext();

    assertEquals("failed", repository.runStatus);
    assertTrue(repository.rollbackFailed);
    assertEquals(null, repository.planStatus);
  }

  @Test
  void timeoutRollbackExecutionMarksRollbackPlanFailedWithoutUpdatingAutomationPlan() {
    FakeRunnerRepository repository = new FakeRunnerRepository();
    repository.expiredRuns.add(
        run(
            "exec_1",
            new RunState("running", "live"),
            "rollback",
            new RollbackRef("rbp_1", "exec_src")));

    ExecutionProperties executionProperties = new ExecutionProperties();
    RunnerProperties runnerProperties = new RunnerProperties();
    runnerProperties.setRunnerId("runner_1");

    RunnerExecutionService service =
        new RunnerExecutionService(
            repository,
            repository,
            executionProperties,
            runnerProperties,
            List.of(new ManualStepExecutor(new ObjectMapper())));

    int count = service.sweepTimeouts();

    assertEquals(1, count);
    assertEquals("timeout", repository.timeoutRunStatus);
    assertTrue(repository.stepsTimedOut);
    assertTrue(repository.rollbackFailed);
    assertEquals(null, repository.planStatus);
  }

  private ExecutionRunRecord run(
      String id, RunState state, String executionKind, RollbackRef rollback) {
    return new ExecutionRunRecord(
        id,
        "tenant_1",
        "inc_1",
        "plan_1",
        state.status(),
        state.mode(),
        "alice",
        "runner_1",
        OffsetDateTime.now(),
        null,
        null,
        null,
        1,
        3,
        null,
        OffsetDateTime.now().plusSeconds(60),
        OffsetDateTime.now(),
        1800,
        null,
        null,
        null,
        null,
        executionKind,
        rollback.rollbackPlanId(),
        rollback.rollbackOfExecutionId(),
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  record RunState(String mode, String status) {}

  record RollbackRef(String rollbackPlanId, String rollbackOfExecutionId) {}

  private ExecutionStepRecord step(String id, int sequence, String actionType, String payload) {
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
        payload,
        "",
        null,
        null,
        null,
        null,
        1,
        300,
        0,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }
}
