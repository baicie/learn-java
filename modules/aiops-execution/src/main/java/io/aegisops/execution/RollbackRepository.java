package io.aegisops.execution;

import io.aegisops.execution.dto.RollbackDecisionCreateCommand;
import io.aegisops.execution.dto.RollbackDecisionRecord;
import io.aegisops.execution.dto.RollbackPlanCreateCommand;
import io.aegisops.execution.dto.RollbackPlanRecord;
import io.aegisops.execution.dto.RollbackPlanStepCreateCommand;
import io.aegisops.execution.dto.RollbackPlanStepRecord;
import java.util.List;
import java.util.Optional;

public interface RollbackRepository {
  void createPlan(RollbackPlanCreateCommand command);

  void createStep(RollbackPlanStepCreateCommand command);

  Optional<RollbackPlanRecord> findRollbackPlan(String tenantId, String rollbackPlanId);

  Optional<RollbackPlanRecord> findLatestRollbackPlanBySourceExecution(
      String tenantId, String sourceExecutionId);

  List<RollbackPlanStepRecord> listSteps(String tenantId, String rollbackPlanId);

  List<RollbackDecisionRecord> listDecisions(String tenantId, String rollbackPlanId);

  boolean updatePlanStatus(
      String tenantId, String rollbackPlanId, String fromStatus, String toStatus);

  boolean updatePlanStatusToCancelled(String tenantId, String rollbackPlanId);

  boolean markCancelled(String tenantId, String rollbackPlanId);

  boolean submitPlan(String tenantId, String rollbackPlanId, String submittedBy);

  void createDecision(RollbackDecisionCreateCommand command);

  boolean decisionExists(String tenantId, String rollbackPlanId, String reviewer);

  int countDecisions(String tenantId, String rollbackPlanId, String decision);

  boolean markApproved(
      String tenantId, String rollbackPlanId, int approvedCount, String approvalSnapshotJson);

  boolean markRejected(
      String tenantId, String rollbackPlanId, int rejectedCount, String approvalSnapshotJson);

  boolean markExecuting(String tenantId, String rollbackPlanId);

  boolean markSucceeded(String tenantId, String rollbackPlanId);

  boolean markFailed(String tenantId, String rollbackPlanId);
}
