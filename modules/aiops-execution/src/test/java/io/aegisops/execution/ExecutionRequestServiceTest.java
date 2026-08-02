package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.dto.ExecutionCreateRequest;
import io.aegisops.execution.dto.ExecutionRetryRequest;
import io.aegisops.execution.dto.ExecutionRunCreateCommand;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionStepCreateCommand;
import io.aegisops.execution.dto.PlanForExecutionRecord;
import io.aegisops.execution.dto.PlanStepForExecutionRecord;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ExecutionRequestServiceTest {
  private static final OffsetDateTime GRANT_EXPIRES_AT =
      OffsetDateTime.parse("2026-08-01T12:30:00Z");

  @Test
  void createExecutionPersistsSignedGrantAndSnapshotMetadata() {
    FakeExecutionRequestRepository repository = new FakeExecutionRequestRepository();
    repository.plan = plan("approved");
    repository.planSteps.add(planStep());

    ExecutionRequestService service =
        new ExecutionRequestService(
            repository,
            new ExecutionProperties(),
            new ObjectMapper(),
            (run, steps) ->
                new IssuedExecutionGrant(
                    "signed.execution.grant", "a".repeat(64), GRANT_EXPIRES_AT));

    service.createExecution("tenant_1", "plan_1", new ExecutionCreateRequest(true, "alice", 3));

    assertEquals("signed.execution.grant", repository.createdRun.executionGrant());
    assertEquals("a".repeat(64), repository.createdRun.executionSnapshotSha256());
    assertEquals(GRANT_EXPIRES_AT, repository.createdRun.executionGrantExpiresAt());
  }

  @Test
  void createExecutionCreatesAttemptMetadata() {
    FakeExecutionRequestRepository repository = new FakeExecutionRequestRepository();
    repository.plan = plan("approved");
    repository.planSteps.add(planStep());

    ExecutionProperties properties = new ExecutionProperties();
    properties.setMaxRetryAttempts(3);

    ExecutionRequestService service =
        new ExecutionRequestService(
            repository,
            properties,
            new ObjectMapper(),
            (run, steps) ->
                new IssuedExecutionGrant(
                    "attempt.execution.grant", "d".repeat(64), GRANT_EXPIRES_AT));

    var response =
        service.createExecution("tenant_1", "plan_1", new ExecutionCreateRequest(true, "alice", 3));

    assertEquals("queued", response.status());
    assertEquals(1, response.attempt());
    assertEquals(3, response.maxAttempts());
    assertEquals("executing", repository.planStatus);
  }

  @Test
  void retryCreatesNextAttempt() {
    FakeExecutionRequestRepository repository = new FakeExecutionRequestRepository();
    repository.plan = plan("failed");
    repository.planSteps.add(planStep());
    repository.existingRun = run("exec_old", "failed", 1, 3, null);
    repository.latestRun = repository.existingRun;

    ExecutionProperties properties = new ExecutionProperties();
    properties.setMaxRetryAttempts(3);

    ExecutionRequestService service =
        new ExecutionRequestService(
            repository,
            properties,
            new ObjectMapper(),
            (run, steps) ->
                new IssuedExecutionGrant(
                    "retry-attempt.execution.grant", "e".repeat(64), GRANT_EXPIRES_AT));

    var response = service.retry("tenant_1", "exec_old", new ExecutionRetryRequest("bob"));

    assertEquals("queued", response.status());
    assertEquals(2, response.attempt());
    assertEquals("exec_old", response.retryOfExecutionId());
    assertEquals("executing", repository.planStatus);
  }

  @Test
  void retryPersistsAReplacementExecutionGrant() {
    FakeExecutionRequestRepository repository = new FakeExecutionRequestRepository();
    repository.plan = plan("failed");
    repository.planSteps.add(planStep());
    repository.existingRun = run("exec_old", "failed", 1, 3, null);
    repository.latestRun = repository.existingRun;

    ExecutionRequestService service =
        new ExecutionRequestService(
            repository,
            new ExecutionProperties(),
            new ObjectMapper(),
            (run, steps) ->
                new IssuedExecutionGrant(
                    "retry.execution.grant", "b".repeat(64), GRANT_EXPIRES_AT));

    service.retry("tenant_1", "exec_old", new ExecutionRetryRequest("bob"));

    assertEquals("retry.execution.grant", repository.createdRun.executionGrant());
    assertEquals("b".repeat(64), repository.createdRun.executionSnapshotSha256());
    assertEquals(GRANT_EXPIRES_AT, repository.createdRun.executionGrantExpiresAt());
  }

  @Test
  void retryRejectsWhenAttemptsExhausted() {
    FakeExecutionRequestRepository repository = new FakeExecutionRequestRepository();
    repository.plan = plan("failed");
    repository.existingRun = run("exec_old", "failed", 3, 3, null);
    repository.latestRun = repository.existingRun;

    ExecutionRequestService service =
        new ExecutionRequestService(repository, new ExecutionProperties(), new ObjectMapper());

    assertThrows(
        Exception.class,
        () -> service.retry("tenant_1", "exec_old", new ExecutionRetryRequest("bob")));
  }

  @Test
  void retryRejectsNonLatestFailedExecution() {
    FakeExecutionRequestRepository repository = new FakeExecutionRequestRepository();
    repository.plan = plan("failed");
    repository.planSteps.add(planStep());
    repository.existingRun = run("exec_old", "failed", 1, 3, null);
    repository.latestRun = run("exec_new", "succeeded", 2, 3, "exec_old");

    ExecutionProperties properties = new ExecutionProperties();
    properties.setMaxRetryAttempts(3);

    ExecutionRequestService service =
        new ExecutionRequestService(repository, properties, new ObjectMapper());

    Exception ex =
        assertThrows(
            Exception.class,
            () -> service.retry("tenant_1", "exec_old", new ExecutionRetryRequest("bob")));

    assertTrue(ex.getMessage().contains("latest"));
  }

  @Test
  void retryReturnsActiveLatestExecutionWhenRetryAlreadyQueued() {
    FakeExecutionRequestRepository repository = new FakeExecutionRequestRepository();
    repository.plan = plan("executing");
    repository.planSteps.add(planStep());
    repository.existingRun = run("exec_old", "failed", 1, 3, null);
    repository.latestRun = run("exec_retry", "queued", 2, 3, "exec_old");

    ExecutionProperties properties = new ExecutionProperties();
    properties.setMaxRetryAttempts(3);

    ExecutionRequestService service =
        new ExecutionRequestService(repository, properties, new ObjectMapper());

    var response = service.retry("tenant_1", "exec_old", new ExecutionRetryRequest("bob"));

    assertEquals("exec_retry", response.id());
    assertEquals("queued", response.status());
  }

  @Test
  void retryRejectsWhenPlanIsNotFailed() {
    FakeExecutionRequestRepository repository = new FakeExecutionRequestRepository();
    repository.plan = plan("succeeded");
    repository.planSteps.add(planStep());
    repository.existingRun = run("exec_old", "failed", 1, 3, null);
    repository.latestRun = repository.existingRun;

    ExecutionProperties properties = new ExecutionProperties();
    properties.setMaxRetryAttempts(3);

    ExecutionRequestService service =
        new ExecutionRequestService(repository, properties, new ObjectMapper());

    Exception ex =
        assertThrows(
            Exception.class,
            () -> service.retry("tenant_1", "exec_old", new ExecutionRetryRequest("bob")));

    assertTrue(ex.getMessage().contains("failed automation plan"));
  }

  @Test
  void cancelExecutionCancelsRunStepsAndPlan() {
    FakeExecutionRequestRepository repository = new FakeExecutionRequestRepository();
    repository.createdRun =
        new ExecutionRunCreateCommand(
            "exec_1",
            "tenant_1",
            "inc_1",
            "plan_1",
            "running",
            "dry_run",
            "alice",
            1,
            1,
            null,
            1800,
            null,
            null,
            null,
            "normal",
            null,
            null,
            null,
            null,
            null);

    repository.createdSteps.add(
        new ExecutionStepCreateCommand(
            "step_1",
            "tenant_1",
            "exec_1",
            "planstep_1",
            1,
            "Check",
            "manual",
            "human",
            "queued",
            "{}",
            "",
            1,
            300));

    ExecutionProperties properties = new ExecutionProperties();
    ExecutionRequestService service =
        new ExecutionRequestService(repository, properties, new ObjectMapper());

    var response = service.cancel("tenant_1", "exec_1");

    assertEquals("exec_1", repository.cancelledExecutionId);
    assertTrue(repository.stepsCancelled);
    assertEquals("cancelled", repository.planStatus);
    assertEquals("execution_cancelled", repository.timelines.get(0).eventType());
  }

  @Test
  void cancelRollbackExecutionCancelsRollbackPlanWithoutUpdatingAutomationPlan() {
    FakeExecutionRequestRepository repository = new FakeExecutionRequestRepository();
    repository.createdRun =
        new ExecutionRunCreateCommand(
            "exec_1",
            "tenant_1",
            "inc_1",
            "plan_1",
            "running",
            "live",
            "alice",
            1,
            1,
            null,
            1800,
            "rbp_1",
            "{\"status\":\"approved\"}",
            "high",
            "rollback",
            "rbp_1",
            "exec_source",
            null,
            null,
            null);

    FakeExecutionRollbackRepository rollback = new FakeExecutionRollbackRepository();
    ExecutionRequestService service =
        new ExecutionRequestService(
            repository, rollback, new ExecutionProperties(), new ObjectMapper());

    service.cancel("tenant_1", "exec_1");

    assertEquals("exec_1", repository.cancelledExecutionId);
    assertTrue(repository.stepsCancelled);
    assertEquals(null, repository.planStatus);
    assertTrue(rollback.cancelled);
  }

  @Test
  void rejectNormalRetryForRollbackExecution() {
    FakeExecutionRequestRepository repository = new FakeExecutionRequestRepository();
    repository.existingRun = rollbackRun();
    repository.latestRun = repository.existingRun;

    ExecutionRequestService service =
        new ExecutionRequestService(
            repository,
            new FakeExecutionRollbackRepository(),
            new ExecutionProperties(),
            new ObjectMapper());

    Exception ex =
        assertThrows(
            Exception.class,
            () -> service.retry("tenant_1", "exec_1", new ExecutionRetryRequest("bob")));

    assertTrue(ex.getMessage().contains("Rollback execution retry"));
    assertEquals(null, repository.planStatus);
  }

  private PlanForExecutionRecord plan(String status) {
    return new PlanForExecutionRecord(
        "plan_1", "tenant_1", "inc_1", status, "medium", "title", "summary");
  }

  private PlanStepForExecutionRecord planStep() {
    return new PlanStepForExecutionRecord(
        "planstep_1",
        "plan_1",
        1,
        "Check",
        "manual",
        "human",
        "{}",
        "desc",
        "ok",
        "rollback",
        true,
        "pending");
  }

  private ExecutionRunRecord run(
      String id, String status, int attempt, int maxAttempts, String retryOf) {
    return new ExecutionRunRecord(
        id,
        "tenant_1",
        "inc_1",
        "plan_1",
        status,
        "dry_run",
        "alice",
        null,
        null,
        null,
        null,
        null,
        attempt,
        maxAttempts,
        retryOf,
        null,
        null,
        1800,
        null,
        null,
        null,
        null,
        "normal",
        null,
        null,
        null,
        null,
        null,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private ExecutionRunRecord rollbackRun() {
    return new ExecutionRunRecord(
        "exec_1",
        "tenant_1",
        "inc_1",
        "plan_1",
        "failed",
        "live",
        "alice",
        null,
        null,
        null,
        null,
        null,
        1,
        3,
        null,
        null,
        null,
        1800,
        null,
        null,
        null,
        null,
        "rollback",
        "rbp_1",
        "exec_source",
        null,
        null,
        null,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }
}
