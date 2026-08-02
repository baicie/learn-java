package io.aegisops.execution;

import io.aegisops.execution.dto.ExecutionArtifactRecord;
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

final class FakeExecutionRequestRepository extends FakeExecutionRepositoryBase {
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
              createdRun.executionKind(),
              createdRun.rollbackPlanId(),
              createdRun.rollbackOfExecutionId(),
              createdRun.executionGrant(),
              createdRun.executionSnapshotSha256(),
              createdRun.executionGrantExpiresAt(),
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
