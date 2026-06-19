package io.aegisops.runner;

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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class FakeRunnerRepository extends RunnerFakeExecutionRepositoryBase implements RollbackRepository {
  ExecutionRunRecord claimed;
  final List<ExecutionStepRecord> steps = new ArrayList<>();
  final List<ExecutionRunRecord> expiredRuns = new ArrayList<>();
  final List<ExecutionArtifactCreateCommand> artifacts = new ArrayList<>();
  final List<String> stepStatuses = new ArrayList<>();
  int heartbeatCount;
  int artifactIncrementCount;
  String runStatus;
  String planStatus;
  String timeoutRunStatus;
  boolean stepsTimedOut;
  boolean failPlanStatusUpdate;
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
  public boolean updatePlanStatusToCancelled(String tenantId, String rollbackPlanId) {
    return true;
  }

  @Override
  public boolean markCancelled(String tenantId, String rollbackPlanId) {
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
  public int countDecisions(String tenantId, String rollbackPlanId, String decision) {
    return 0;
  }

  @Override
  public boolean markApproved(
      String tenantId, String rollbackPlanId, int approvedCount, String approvalSnapshotJson) {
    return true;
  }

  @Override
  public boolean markRejected(
      String tenantId, String rollbackPlanId, int rejectedCount, String approvalSnapshotJson) {
    return true;
  }

  @Override
  public boolean markExecuting(String tenantId, String rollbackPlanId) {
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
}
