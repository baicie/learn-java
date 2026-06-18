package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.ExecutionArtifactRecord;
import io.aegisops.execution.dto.ExecutionCreateRequest;
import io.aegisops.execution.dto.ExecutionRetryRequest;
import io.aegisops.execution.dto.ExecutionRunCreateCommand;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionStepCreateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.execution.dto.PlanForExecutionRecord;
import io.aegisops.execution.dto.PlanStepForExecutionRecord;
import io.aegisops.execution.dto.TimelineCreateCommand;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExecutionRequestServiceTest {
  @Test
  void createExecutionCreatesAttemptMetadata() {
    FakeExecutionRepository repository = new FakeExecutionRepository();
    repository.plan = plan("approved");
    repository.planSteps.add(planStep());

    ExecutionProperties properties = new ExecutionProperties();
    properties.setMaxRetryAttempts(3);

    ExecutionRequestService service =
        new ExecutionRequestService(repository, properties, new ObjectMapper());

    var response =
        service.createExecution("tenant_1", "plan_1", new ExecutionCreateRequest(true, "alice", 3));

    assertEquals("queued", response.status());
    assertEquals(1, response.attempt());
    assertEquals(3, response.maxAttempts());
    assertEquals("executing", repository.planStatus);
  }

  @Test
  void retryCreatesNextAttempt() {
    FakeExecutionRepository repository = new FakeExecutionRepository();
    repository.plan = plan("failed");
    repository.planSteps.add(planStep());
    repository.existingRun = run("exec_old", "failed", 1, 3, null);
    repository.latestRun = repository.existingRun;

    ExecutionProperties properties = new ExecutionProperties();
    properties.setMaxRetryAttempts(3);

    ExecutionRequestService service =
        new ExecutionRequestService(repository, properties, new ObjectMapper());

    var response = service.retry("tenant_1", "exec_old", new ExecutionRetryRequest("bob"));

    assertEquals("queued", response.status());
    assertEquals(2, response.attempt());
    assertEquals("exec_old", response.retryOfExecutionId());
    assertEquals("executing", repository.planStatus);
  }

  @Test
  void retryRejectsWhenAttemptsExhausted() {
    FakeExecutionRepository repository = new FakeExecutionRepository();
    repository.plan = plan("failed");
    repository.existingRun = run("exec_old", "failed", 3, 3, null);
    repository.latestRun = repository.existingRun;

    ExecutionRequestService service =
        new ExecutionRequestService(repository, new ExecutionProperties(), new ObjectMapper());

    assertThrows(
        AppException.class,
        () -> service.retry("tenant_1", "exec_old", new ExecutionRetryRequest("bob")));
  }

  @Test
  void retryRejectsNonLatestFailedExecution() {
    FakeExecutionRepository repository = new FakeExecutionRepository();
    repository.plan = plan("failed");
    repository.planSteps.add(planStep());
    repository.existingRun = run("exec_old", "failed", 1, 3, null);
    repository.latestRun = run("exec_new", "succeeded", 2, 3, "exec_old");

    ExecutionProperties properties = new ExecutionProperties();
    properties.setMaxRetryAttempts(3);

    ExecutionRequestService service =
        new ExecutionRequestService(repository, properties, new ObjectMapper());

    AppException ex =
        assertThrows(
            AppException.class,
            () -> service.retry("tenant_1", "exec_old", new ExecutionRetryRequest("bob")));

    assertTrue(ex.getMessage().contains("latest"));
  }

  @Test
  void retryReturnsActiveLatestExecutionWhenRetryAlreadyQueued() {
    FakeExecutionRepository repository = new FakeExecutionRepository();
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
    FakeExecutionRepository repository = new FakeExecutionRepository();
    repository.plan = plan("succeeded");
    repository.planSteps.add(planStep());
    repository.existingRun = run("exec_old", "failed", 1, 3, null);
    repository.latestRun = repository.existingRun;

    ExecutionProperties properties = new ExecutionProperties();
    properties.setMaxRetryAttempts(3);

    ExecutionRequestService service =
        new ExecutionRequestService(repository, properties, new ObjectMapper());

    AppException ex =
        assertThrows(
            AppException.class,
            () -> service.retry("tenant_1", "exec_old", new ExecutionRetryRequest("bob")));

    assertTrue(ex.getMessage().contains("failed automation plan"));
  }

  @Test
  void cancelExecutionCancelsRunStepsAndPlan() {
    FakeExecutionRepository repository = new FakeExecutionRepository();
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
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private static class FakeExecutionRepository extends FakeExecutionRepositoryBase {
    PlanForExecutionRecord plan;
    String planStatus;
    ExecutionRunRecord existingRun;
    ExecutionRunRecord latestRun;
    ExecutionRunCreateCommand createdRun;
    final List<PlanStepForExecutionRecord> planSteps = new ArrayList<>();
    final List<ExecutionStepCreateCommand> createdSteps = new ArrayList<>();
    final List<TimelineCreateCommand> timelines = new ArrayList<>();
    String cancelledExecutionId;
    boolean stepsCancelled;

    @Override
    public Optional<PlanForExecutionRecord> findPlan(String tenantId, String planId) {
      return Optional.ofNullable(plan);
    }

    @Override
    public List<PlanStepForExecutionRecord> listPlanSteps(String planId) {
      return planSteps;
    }

    @Override
    public Optional<ExecutionRunRecord> findRun(String tenantId, String executionId) {
      if (createdRun != null && createdRun.id().equals(executionId)) {
        return Optional.of(
            new ExecutionRunRecord(
                createdRun.id(),
                createdRun.tenantId(),
                createdRun.incidentId(),
                createdRun.planId(),
                createdRun.status(),
                createdRun.mode(),
                createdRun.requestedBy(),
                null,
                null,
                null,
                null,
                null,
                createdRun.attempt(),
                createdRun.maxAttempts(),
                createdRun.retryOfExecutionId(),
                null,
                null,
                createdRun.timeoutSeconds(),
                null,
                null,
                null,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()));
      }
      return Optional.ofNullable(existingRun);
    }

    @Override
    public Optional<ExecutionRunRecord> findLatestRunByPlan(String tenantId, String planId) {
      return Optional.ofNullable(latestRun != null ? latestRun : existingRun);
    }

    @Override
    public void createRun(ExecutionRunCreateCommand command) {
      createdRun = command;
    }

    @Override
    public void createSteps(List<ExecutionStepCreateCommand> commands) {
      createdSteps.addAll(commands);
    }

    @Override
    public boolean updatePlanStatus(String tenantId, String planId, String status) {
      planStatus = status;
      return true;
    }

    @Override
    public List<ExecutionStepRecord> listExecutionSteps(String tenantId, String executionId) {
      return List.of();
    }

    @Override
    public List<ExecutionArtifactRecord> listArtifacts(String tenantId, String executionId) {
      return List.of();
    }

    @Override
    public void addTimeline(TimelineCreateCommand command) {
      timelines.add(command);
    }

    @Override
    public boolean cancelRun(String tenantId, String executionId) {
      cancelledExecutionId = executionId;
      return true;
    }

    @Override
    public boolean cancelExecutionSteps(String tenantId, String executionId) {
      stepsCancelled = true;
      return true;
    }
  }
}
