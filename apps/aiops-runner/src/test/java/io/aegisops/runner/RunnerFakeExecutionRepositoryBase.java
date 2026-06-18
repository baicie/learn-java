package io.aegisops.runner;

import io.aegisops.execution.ExecutionRepository;
import io.aegisops.execution.dto.ExecutionApprovalSnapshotRecord;
import io.aegisops.execution.dto.ExecutionArtifactCreateCommand;
import io.aegisops.execution.dto.ExecutionArtifactRecord;
import io.aegisops.execution.dto.ExecutionRunCreateCommand;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionRunStatusUpdateCommand;
import io.aegisops.execution.dto.ExecutionStepCreateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.execution.dto.ExecutionStepStatusUpdateCommand;
import io.aegisops.execution.dto.PlanForExecutionRecord;
import io.aegisops.execution.dto.PlanStepForExecutionRecord;
import io.aegisops.execution.dto.TimelineCreateCommand;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

abstract class RunnerFakeExecutionRepositoryBase implements ExecutionRepository {
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
  public List<ExecutionArtifactRecord> listArtifacts(String tenantId, String executionId) {
    return List.of();
  }

  @Override
  public void createRun(ExecutionRunCreateCommand command) {}

  @Override
  public void createSteps(List<ExecutionStepCreateCommand> commands) {}

  @Override
  public void createArtifact(ExecutionArtifactCreateCommand command) {}

  @Override
  public boolean incrementStepArtifactCount(String tenantId, String stepId) {
    return true;
  }

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
  public Optional<ExecutionRunRecord> claimNextQueuedRun(
      String runnerId, OffsetDateTime now, OffsetDateTime leaseUntil) {
    return Optional.empty();
  }

  @Override
  public boolean heartbeat(
      String tenantId,
      String executionId,
      String runnerId,
      OffsetDateTime heartbeatAt,
      OffsetDateTime leaseUntil) {
    return true;
  }

  @Override
  public List<ExecutionRunRecord> findExpiredRunningRuns(OffsetDateTime now, int limit) {
    return List.of();
  }

  @Override
  public boolean timeoutRun(String tenantId, String executionId, String errorMessage) {
    return true;
  }

  @Override
  public boolean timeoutExecutionSteps(String tenantId, String executionId) {
    return true;
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

  @Override
  public Optional<ExecutionApprovalSnapshotRecord> findLatestApprovedApprovalSnapshot(
      String tenantId, String planId) {
    return Optional.empty();
  }

  @Override
  public boolean markLiveGuardPassed(String tenantId, String executionId) {
    return true;
  }
}
