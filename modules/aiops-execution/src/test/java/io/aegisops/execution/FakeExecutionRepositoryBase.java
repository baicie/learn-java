package io.aegisops.execution;

import io.aegisops.execution.dto.ExecutionRunCreateCommand;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionRunStatusUpdateCommand;
import io.aegisops.execution.dto.ExecutionStepCreateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.execution.dto.ExecutionStepStatusUpdateCommand;
import io.aegisops.execution.dto.PlanForExecutionRecord;
import io.aegisops.execution.dto.PlanStepForExecutionRecord;
import io.aegisops.execution.dto.TimelineCreateCommand;
import java.util.List;
import java.util.Optional;

abstract class FakeExecutionRepositoryBase implements ExecutionRepository {
  @Override
  public Optional<PlanForExecutionRecord> findPlan(String tenantId, String planId) {
    return Optional.empty();
  }

  @Override
  public List<PlanStepForExecutionRecord> listPlanSteps(String planId) {
    return List.of();
  }

  @Override
  public Optional<ExecutionRunRecord> findLatestRunByPlan(String tenantId, String planId) {
    return Optional.empty();
  }

  @Override
  public Optional<ExecutionRunRecord> findRun(String tenantId, String executionId) {
    return Optional.empty();
  }

  @Override
  public List<ExecutionStepRecord> listExecutionSteps(String tenantId, String executionId) {
    return List.of();
  }

  @Override
  public void createRun(ExecutionRunCreateCommand command) {}

  @Override
  public void createSteps(List<ExecutionStepCreateCommand> commands) {}

  @Override
  public boolean updatePlanStatus(String tenantId, String planId, String status) {
    return true;
  }

  @Override
  public boolean cancelRun(String tenantId, String executionId) {
    return true;
  }

  @Override
  public boolean cancelExecutionSteps(String tenantId, String executionId) {
    return true;
  }

  @Override
  public Optional<ExecutionRunRecord> claimNextQueuedRun(String runnerId) {
    return Optional.empty();
  }

  @Override
  public boolean updateRunStatus(ExecutionRunStatusUpdateCommand command) {
    return true;
  }

  @Override
  public boolean updateStepStatus(ExecutionStepStatusUpdateCommand command) {
    return true;
  }

  @Override
  public void addTimeline(TimelineCreateCommand command) {}
}
