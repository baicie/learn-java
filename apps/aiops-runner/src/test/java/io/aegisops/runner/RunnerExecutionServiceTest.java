package io.aegisops.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.aegisops.runner.executor.ManualStepExecutor;
import io.aegisops.runner.executor.ShellDryRunStepExecutor;
import io.aegisops.runner.executor.StepExecutionContext;
import io.aegisops.runner.executor.StepExecutionResult;
import io.aegisops.runner.executor.StepExecutor;
import io.aegisops.runner.executor.UnsupportedStepExecutor;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RunnerExecutionServiceTest {

  @Test
  void processRunWritesHeartbeatAndArtifacts() {
    Harness harness = new Harness();
    harness.execution.claimed =
        harness.run(
            "exec_1", new RunState("dry_run", "running"), "normal", new RollbackRef(null, null));
    harness.execution.steps.add(harness.step("step_1", 1, "manual", "{}"));

    harness.build().processNext();

    assertTrue(harness.execution.heartbeatCount >= 2);
    assertEquals("succeeded", harness.execution.runStatus);
    assertEquals("succeeded", harness.execution.planStatus);
    assertEquals(1, harness.execution.artifacts.size());
    assertEquals(1, harness.execution.artifactIncrementCount);
  }

  @Test
  void missingExecutionGrantFailsBeforeAnyStepExecutorRuns() {
    Harness harness = new Harness();
    harness.execution.claimed =
        harness.withGrant(
            harness.run(
                "exec_1",
                new RunState("dry_run", "running"),
                "normal",
                new RollbackRef(null, null)),
            "super-secret.execution.grant");
    harness.execution.steps.add(harness.step("step_1", 1, "manual", "{}"));
    CountingStepExecutor executor = new CountingStepExecutor();

    harness
        .buildWithValidatorAndExecutors(
            (run, steps) -> {
              throw new InvalidExecutionGrantException("signature contained sensitive detail");
            },
            executor)
        .processNext();

    assertEquals("failed", harness.execution.runStatus);
    assertEquals("Execution grant validation failed.", harness.execution.runErrorMessage);
    assertEquals("failed", harness.execution.planStatus);
    assertEquals(1, harness.execution.auditEvents.size());
    assertEquals("execution_grant_rejected", harness.execution.auditEvents.get(0).eventType());
    assertEquals("aiops-runner", harness.execution.auditEvents.get(0).actor());
    assertEquals(
        "{\"reason\":\"grant_validation_failed\"}",
        harness.execution.auditEvents.get(0).payloadJson());
    assertTrue(
        !harness.execution.auditEvents.get(0).toString().contains("super-secret.execution.grant"));
    assertEquals(0, executor.calls);
    assertEquals(0, harness.execution.heartbeatCount);
    assertEquals(0, harness.execution.artifacts.size());
  }

  @Test
  void invalidRollbackGrantFailsRollbackPlanAndWritesAuditWithoutUpdatingAutomationPlan() {
    Harness harness = new Harness();
    harness.execution.claimed =
        harness.run(
            "exec_1",
            new RunState("live", "running"),
            "rollback",
            new RollbackRef("rbp_1", "exec_src"));
    harness.execution.steps.add(harness.step("step_1", 1, "manual", "{}"));

    harness
        .buildWithValidator(
            (run, steps) -> {
              throw new InvalidExecutionGrantException("expired");
            })
        .processNext();

    assertEquals("failed", harness.execution.runStatus);
    assertTrue(harness.rollback.rollbackFailed);
    assertEquals(null, harness.execution.planStatus);
    assertEquals(1, harness.execution.auditEvents.size());
  }

  @Test
  void timeoutSweepMarksRunStepsAndPlan() {
    Harness harness = new Harness();
    harness.execution.expiredRuns.add(
        harness.run(
            "exec_1", new RunState("dry_run", "running"), "normal", new RollbackRef(null, null)));

    int count = harness.build().sweepTimeouts();

    assertEquals(1, count);
    assertEquals("timeout", harness.execution.timeoutRunStatus);
    assertEquals("failed", harness.execution.planStatus);
    assertTrue(harness.execution.stepsTimedOut);
  }

  @Test
  void unsupportedStepFailsRunAndSkipsRemaining() {
    Harness harness = new Harness();
    harness.execution.claimed =
        harness.run(
            "exec_1", new RunState("dry_run", "running"), "normal", new RollbackRef(null, null));
    harness.execution.steps.add(harness.step("step_1", 1, "http", "{}"));
    harness.execution.steps.add(harness.step("step_2", 2, "manual", "{}"));

    harness.build().processNext();

    assertEquals("failed", harness.execution.runStatus);
    assertEquals("failed", harness.execution.planStatus);
    assertTrue(harness.execution.stepStatuses.contains("failed"));
    assertTrue(harness.execution.stepStatuses.contains("skipped"));
  }

  @Test
  void emptyStepsFailRunAndPlan() {
    Harness harness = new Harness();
    harness.execution.claimed =
        harness.run(
            "exec_1", new RunState("dry_run", "running"), "normal", new RollbackRef(null, null));

    harness.build().processNext();

    assertEquals("failed", harness.execution.runStatus);
    assertEquals("failed", harness.execution.planStatus);
  }

  @Test
  void processFailsWhenPlanStatusUpdateFails() {
    Harness harness = new Harness();
    harness.execution.claimed =
        harness.run(
            "exec_1", new RunState("dry_run", "running"), "normal", new RollbackRef(null, null));
    harness.execution.failPlanStatusUpdate = true;
    harness.execution.steps.add(harness.step("step_1", 1, "manual", "{}"));

    assertThrows(AppException.class, () -> harness.build().processNext());
  }

  @Test
  void markRollbackPlanSucceededWhenRollbackRunSucceeded() {
    Harness harness = new Harness();
    harness.execution.claimed =
        harness.run(
            "exec_1",
            new RunState("running", "live"),
            "rollback",
            new RollbackRef("rbp_1", "exec_src"));
    harness.execution.steps.add(harness.step("step_1", 1, "manual", "{}"));

    harness.build().processNext();

    assertEquals("succeeded", harness.execution.runStatus);
    assertTrue(harness.rollback.rollbackSucceeded);
    assertEquals(null, harness.execution.planStatus);
  }

  @Test
  void markRollbackPlanFailedWhenRollbackRunFailed() {
    Harness harness = new Harness();
    harness.execution.claimed =
        harness.run(
            "exec_1",
            new RunState("running", "live"),
            "rollback",
            new RollbackRef("rbp_1", "exec_src"));
    harness.execution.steps.add(harness.step("step_1", 1, "http", "{}"));

    harness.build().processNext();

    assertEquals("failed", harness.execution.runStatus);
    assertTrue(harness.rollback.rollbackFailed);
    assertEquals(null, harness.execution.planStatus);
  }

  @Test
  void timeoutRollbackExecutionMarksRollbackPlanFailedWithoutUpdatingAutomationPlan() {
    Harness harness = new Harness();
    harness.execution.expiredRuns.add(
        harness.run(
            "exec_1",
            new RunState("running", "live"),
            "rollback",
            new RollbackRef("rbp_1", "exec_src")));

    int count = harness.build().sweepTimeouts();

    assertEquals(1, count);
    assertEquals("timeout", harness.execution.timeoutRunStatus);
    assertTrue(harness.execution.stepsTimedOut);
    assertTrue(harness.rollback.rollbackFailed);
    assertEquals(null, harness.execution.planStatus);
  }

  record RunState(String mode, String status) {}

  record RollbackRef(String rollbackPlanId, String rollbackOfExecutionId) {}

  /**
   * Bundles the two ApplicationService fakes plus the small builders each test needs. Stays
   * package-private so the test exercises the same surface a Spring context would wire.
   */
  private static final class Harness {
    final FakeExecutionApplicationService execution = new FakeExecutionApplicationService();
    final FakeRollbackApplicationService rollback = new FakeRollbackApplicationService();

    RunnerExecutionService build() {
      return buildWithValidatorAndExecutors(
          (run, steps) -> {},
          new ManualStepExecutor(new ObjectMapper()),
          new ShellDryRunStepExecutor(new ObjectMapper()),
          new UnsupportedStepExecutor(new ObjectMapper()));
    }

    RunnerExecutionService buildWithValidator(ExecutionGrantValidator validator) {
      return buildWithValidatorAndExecutors(
          validator,
          new ManualStepExecutor(new ObjectMapper()),
          new ShellDryRunStepExecutor(new ObjectMapper()),
          new UnsupportedStepExecutor(new ObjectMapper()));
    }

    RunnerExecutionService buildWithExecutors(
        io.aegisops.runner.executor.StepExecutor... executors) {
      return buildWithValidatorAndExecutors((run, steps) -> {}, executors);
    }

    RunnerExecutionService buildWithValidatorAndExecutors(
        ExecutionGrantValidator validator, StepExecutor... executors) {
      ExecutionProperties executionProperties = new ExecutionProperties();
      executionProperties.setLeaseSeconds(60);
      RunnerProperties runnerProperties = new RunnerProperties();
      runnerProperties.setRunnerId("runner_1");
      return new RunnerExecutionService(
          execution,
          rollback,
          executionProperties,
          runnerProperties,
          List.of(executors),
          validator);
    }

    ExecutionRunRecord run(String id, RunState state, String executionKind, RollbackRef rollback) {
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
          null, // finishedAt
          null, // errorMessage
          null, // summary
          1, // attempt
          3, // maxAttempts
          null, // retryOfExecutionId
          OffsetDateTime.now().plusSeconds(60), // leaseUntil
          OffsetDateTime.now(), // heartbeatAt
          1800, // timeoutSeconds
          null, // approvalId
          null, // approvalSnapshotJson
          null, // planRiskLevel
          null, // liveGuardPassedAt
          executionKind,
          rollback.rollbackPlanId(),
          rollback.rollbackOfExecutionId(),
          null,
          null,
          null,
          OffsetDateTime.now(), // createdAt
          OffsetDateTime.now()); // updatedAt
    }

    ExecutionStepRecord step(String id, int sequence, String actionType, String payload) {
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

    ExecutionRunRecord withGrant(ExecutionRunRecord run, String token) {
      return new ExecutionRunRecord(
          run.id(),
          run.tenantId(),
          run.incidentId(),
          run.planId(),
          run.status(),
          run.mode(),
          run.requestedBy(),
          run.runnerId(),
          run.startedAt(),
          run.finishedAt(),
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
          token,
          "a".repeat(64),
          OffsetDateTime.now().plusHours(1),
          run.createdAt(),
          run.updatedAt());
    }
  }

  private static final class CountingStepExecutor implements StepExecutor {
    int calls;

    @Override
    public boolean supports(String actionType) {
      return true;
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context, ExecutionStepRecord step) {
      calls++;
      return StepExecutionResult.success("ok");
    }
  }

  /** In-memory stand-in for {@link ExecutionApplicationService} used by runner unit tests. */
  private static final class FakeExecutionApplicationService
      implements ExecutionApplicationService {
    ExecutionRunRecord claimed;
    final List<ExecutionStepRecord> steps = new ArrayList<>();
    final List<ExecutionRunRecord> expiredRuns = new ArrayList<>();
    final List<ExecutionArtifactCreateCommand> artifacts = new ArrayList<>();
    final List<ExecutionAuditEventCreateCommand> auditEvents = new ArrayList<>();
    final List<String> stepStatuses = new ArrayList<>();
    int heartbeatCount;
    int artifactIncrementCount;
    String runStatus;
    String runErrorMessage;
    String planStatus;
    String timeoutRunStatus;
    boolean stepsTimedOut;
    boolean failPlanStatusUpdate;

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
    public boolean updateStepStatus(ExecutionStepStatusUpdateCommand command) {
      stepStatuses.add(command.status());
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
    public boolean updateRunStatus(ExecutionRunStatusUpdateCommand command) {
      runStatus = command.status();
      runErrorMessage = command.errorMessage();
      return true;
    }

    @Override
    public boolean markLiveGuardPassed(String tenantId, String executionId) {
      return true;
    }

    @Override
    public void appendAuditEvent(ExecutionAuditEventCreateCommand command) {
      auditEvents.add(command);
    }
  }

  /** In-memory stand-in for {@link RollbackApplicationService} used by runner unit tests. */
  private static final class FakeRollbackApplicationService implements RollbackApplicationService {
    boolean rollbackSucceeded;
    boolean rollbackFailed;

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
  }
}
