package io.aegisops.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.ExecutionProperties;
import io.aegisops.execution.RollbackRepository;
import io.aegisops.execution.dto.ExecutionArtifactCreateCommand;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionRunStatusUpdateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.execution.dto.ExecutionStepStatusUpdateCommand;
import io.aegisops.execution.dto.RollbackDecisionCreateCommand;
import io.aegisops.execution.dto.RollbackDecisionRecord;
import io.aegisops.execution.dto.RollbackPlanCreateCommand;
import io.aegisops.execution.dto.RollbackPlanRecord;
import io.aegisops.execution.dto.RollbackPlanStepCreateCommand;
import io.aegisops.execution.dto.RollbackPlanStepRecord;
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

  private static class FakeRunnerRepository extends RunnerFakeExecutionRepositoryBase
      implements RollbackRepository {
    ExecutionRunRecord claimed;
    final List<ExecutionStepRecord> steps = new ArrayList<>();
    final List<ExecutionRunRecord> expiredRuns = new ArrayList<>();
    final List<ExecutionArtifactCreateCommand> artifacts = new ArrayList<>();
    int heartbeatCount;
    int artifactIncrementCount;
    String runStatus;
    String planStatus;
    String timeoutRunStatus;
    boolean stepsTimedOut;
    boolean failPlanStatusUpdate;
    final List<String> stepStatuses = new ArrayList<>();
    boolean rollbackSucceeded;
    boolean rollbackFailed;

    @Override
    public Optional<ExecutionRunRecord> claimNextQueuedRun(
        String runnerId, OffsetDateTime now, OffsetDateTime leaseUntil) {
      return Optional.ofNullable(claimed);
    }

    @Override
    public List<ExecutionStepRecord> listExecutionSteps(String tenantId, String executionId) {
      return steps;
    }

    @Override
    public boolean heartbeat(
        String tenantId,
        String executionId,
        String runnerId,
        OffsetDateTime heartbeatAt,
        OffsetDateTime leaseUntil) {
      heartbeatCount++;
      return true;
    }

    @Override
    public void createArtifact(ExecutionArtifactCreateCommand command) {
      artifacts.add(command);
    }

    @Override
    public boolean incrementStepArtifactCount(String tenantId, String stepId) {
      artifactIncrementCount++;
      return true;
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

    @Override
    public List<ExecutionRunRecord> findExpiredRunningRuns(OffsetDateTime now, int limit) {
      return expiredRuns;
    }

    @Override
    public boolean timeoutRun(String tenantId, String executionId, String errorMessage) {
      timeoutRunStatus = "timeout";
      return true;
    }

    @Override
    public boolean timeoutExecutionSteps(String tenantId, String executionId) {
      stepsTimedOut = true;
      return true;
    }

    @Override
    public boolean markLiveGuardPassed(String tenantId, String executionId) {
      return true;
    }

    @Override
    public boolean markSucceeded(String tenantId, String rollbackPlanId) {
      rollbackSucceeded = true;
      return true;
    }

    @Override
    public boolean markFailed(String tenantId, String rollbackPlanId) {
      rollbackFailed = true;
      return true;
    }

    @Override
    public void createPlan(RollbackPlanCreateCommand command) {}

    @Override
    public void createStep(RollbackPlanStepCreateCommand command) {}

    @Override
    public Optional<RollbackPlanRecord> findRollbackPlan(String tenantId, String rollbackPlanId) {
      return Optional.empty();
    }

    @Override
    public Optional<RollbackPlanRecord> findLatestRollbackPlanBySourceExecution(
        String tenantId, String sourceExecutionId) {
      return Optional.empty();
    }

    @Override
    public List<RollbackPlanStepRecord> listSteps(String tenantId, String rollbackPlanId) {
      return List.of();
    }

    @Override
    public List<RollbackDecisionRecord> listDecisions(String tenantId, String rollbackPlanId) {
      return List.of();
    }

    @Override
    public boolean updatePlanStatus(
        String tenantId, String rollbackPlanId, String fromStatus, String toStatus) {
      return true;
    }

    @Override
    public boolean submitPlan(String tenantId, String rollbackPlanId, String submittedBy) {
      return true;
    }

    @Override
    public void createDecision(RollbackDecisionCreateCommand command) {}

    @Override
    public boolean decisionExists(String tenantId, String rollbackPlanId, String reviewer) {
      return false;
    }

    @Override
    public boolean markApproved(
        String tenantId, String rollbackPlanId, String approvalSnapshotJson) {
      return true;
    }

    @Override
    public boolean markRejected(
        String tenantId, String rollbackPlanId, String approvalSnapshotJson) {
      return true;
    }

    @Override
    public boolean markExecuting(String tenantId, String rollbackPlanId) {
      return true;
    }
  }
}
