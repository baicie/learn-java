package io.aegisops.execution;

import io.aegisops.execution.dto.RollbackDecisionCreateCommand;
import io.aegisops.execution.dto.RollbackDecisionRecord;
import io.aegisops.execution.dto.RollbackPlanCreateCommand;
import io.aegisops.execution.dto.RollbackPlanRecord;
import io.aegisops.execution.dto.RollbackPlanStepCreateCommand;
import io.aegisops.execution.dto.RollbackPlanStepRecord;
import java.util.List;
import java.util.Optional;

class FakeExecutionRollbackRepository implements RollbackRepository {
  boolean cancelled;

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
    cancelled = true;
    return true;
  }

  @Override
  public boolean markCancelled(String tenantId, String rollbackPlanId) {
    cancelled = true;
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
    return true;
  }

  @Override
  public boolean markFailed(String tenantId, String rollbackPlanId) {
    return true;
  }
}
