package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.ExecutionCreateRequest;
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
  void createExecutionRejectsNonApprovedPlan() {
    FakeExecutionRepository repository = new FakeExecutionRepository();
    repository.plan = plan("draft");

    ExecutionProperties properties = new ExecutionProperties();
    ExecutionRequestService service =
        new ExecutionRequestService(repository, properties, new ObjectMapper());

    assertThrows(
        AppException.class,
        () ->
            service.createExecution(
                "tenant_1", "plan_1", new ExecutionCreateRequest(true, "alice")));
  }

  @Test
  void createExecutionCreatesQueuedDryRun() {
    FakeExecutionRepository repository = new FakeExecutionRepository();
    repository.plan = plan("approved");
    repository.planSteps.add(
        new PlanStepForExecutionRecord(
            "planstep_1",
            "plan_1",
            1,
            "Check",
            "manual",
            "human",
            "{\"executionAllowed\":false}",
            "desc",
            "ok",
            "rollback",
            true,
            "pending"));

    ExecutionProperties properties = new ExecutionProperties();
    ExecutionRequestService service =
        new ExecutionRequestService(repository, properties, new ObjectMapper());

    var response =
        service.createExecution("tenant_1", "plan_1", new ExecutionCreateRequest(true, "alice"));

    assertEquals("queued", response.status());
    assertEquals("dry_run", response.mode());
    assertEquals("executing", repository.planStatus);
    assertEquals(1, response.steps().size());
    assertEquals("execution_queued", repository.timelines.get(0).eventType());
  }

  @Test
  void createExecutionRejectsLiveWhenDisabled() {
    FakeExecutionRepository repository = new FakeExecutionRepository();
    repository.plan = plan("approved");

    ExecutionProperties properties = new ExecutionProperties();
    properties.setLiveEnabled(false);

    ExecutionRequestService service =
        new ExecutionRequestService(repository, properties, new ObjectMapper());

    assertThrows(
        AppException.class,
        () ->
            service.createExecution(
                "tenant_1", "plan_1", new ExecutionCreateRequest(false, "alice")));
  }

  private PlanForExecutionRecord plan(String status) {
    return new PlanForExecutionRecord(
        "plan_1", "tenant_1", "inc_1", status, "medium", "title", "summary");
  }

  private static class FakeExecutionRepository extends FakeExecutionRepositoryBase {
    PlanForExecutionRecord plan;
    String planStatus;
    final List<PlanStepForExecutionRecord> planSteps = new ArrayList<>();
    final List<TimelineCreateCommand> timelines = new ArrayList<>();
    ExecutionRunCreateCommand createdRun;
    final List<ExecutionStepCreateCommand> createdSteps = new ArrayList<>();

    @Override
    public Optional<PlanForExecutionRecord> findPlan(String tenantId, String planId) {
      return Optional.ofNullable(plan);
    }

    @Override
    public List<PlanStepForExecutionRecord> listPlanSteps(String planId) {
      return planSteps;
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
    public Optional<ExecutionRunRecord> findRun(String tenantId, String executionId) {
      if (createdRun == null || !createdRun.id().equals(executionId)) {
        return Optional.empty();
      }
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
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public List<ExecutionStepRecord> listExecutionSteps(String tenantId, String executionId) {
      return createdSteps.stream()
          .map(
              item ->
                  new ExecutionStepRecord(
                      item.id(),
                      item.tenantId(),
                      item.executionId(),
                      item.planStepId(),
                      item.sequenceNo(),
                      item.name(),
                      item.actionType(),
                      item.targetType(),
                      item.status(),
                      item.actionPayloadJson(),
                      item.commandSnapshot(),
                      null,
                      null,
                      null,
                      null,
                      OffsetDateTime.now(),
                      OffsetDateTime.now()))
          .toList();
    }

    @Override
    public void addTimeline(TimelineCreateCommand command) {
      timelines.add(command);
    }
  }
}
