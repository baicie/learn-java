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

public interface ExecutionRepository {
  Optional<PlanForExecutionRecord> findPlan(String tenantId, String planId);

  List<PlanStepForExecutionRecord> listPlanSteps(String planId);

  Optional<ExecutionRunRecord> findLatestRunByPlan(String tenantId, String planId);

  Optional<ExecutionRunRecord> findRun(String tenantId, String executionId);

  List<ExecutionStepRecord> listExecutionSteps(String tenantId, String executionId);

  void createRun(ExecutionRunCreateCommand command);

  void createSteps(List<ExecutionStepCreateCommand> commands);

  boolean updatePlanStatus(String tenantId, String planId, String status);

  boolean cancelRun(String tenantId, String executionId);

  Optional<ExecutionRunRecord> claimNextQueuedRun(String runnerId);

  boolean updateRunStatus(ExecutionRunStatusUpdateCommand command);

  boolean updateStepStatus(ExecutionStepStatusUpdateCommand command);

  void addTimeline(TimelineCreateCommand command);
}
