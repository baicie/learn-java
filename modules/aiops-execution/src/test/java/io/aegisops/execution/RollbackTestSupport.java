package io.aegisops.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.dto.ExecutionArtifactResponse;
import io.aegisops.execution.dto.ExecutionRunCreateCommand;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionRunResponse;
import io.aegisops.execution.dto.ExecutionStepCreateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.execution.dto.ExecutionStepResponse;
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

/** 测试支持类:统一构造回滚相关 DTO 与 fake 仓库。 */
final class RollbackTestSupport {
  private RollbackTestSupport() {}

  static ExecutionRunRecord executionRecord(String mode, String status, String executionKind) {
    return executionRecord(
        new RunKey("exec_1", "tenant_1", "inc_1", "plan_1"),
        new RunState(mode, status, executionKind));
  }

  static ExecutionRunRecord executionRecord(RunKey key, RunState state) {
    return new ExecutionRunRecord(
        key.id(),
        key.tenantId(),
        key.incidentId(),
        key.planId(),
        state.status(),
        state.mode(),
        "alice",
        "runner_1",
        OffsetDateTime.now(),
        null,
        null,
        null,
        1,
        1,
        null,
        OffsetDateTime.now().plusSeconds(60),
        OffsetDateTime.now(),
        1800,
        null,
        null,
        "high",
        null,
        state.executionKind(),
        null,
        null,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  record RunKey(String id, String tenantId, String incidentId, String planId) {}

  record RunState(String mode, String status, String executionKind) {}

  static ExecutionStepRecord stepRecord(
      String id, int sequence, String actionType, String payload) {
    return new ExecutionStepRecord(
        id,
        "tenant_1",
        "exec_1",
        "planstep_" + sequence,
        sequence,
        "step " + sequence,
        actionType,
        "host",
        "succeeded",
        payload,
        "",
        null,
        null,
        null,
        null,
        1,
        300,
        0,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  static RollbackPlanRecord rollbackPlanRecord(String tenantId, String incidentId, String status) {
    return rollbackPlanRecord(new RollbackKey("rbp_1", tenantId, incidentId, status, 0, 1));
  }

  static RollbackPlanRecord rollbackPlanRecord(
      String id, String tenantId, String incidentId, String status) {
    return rollbackPlanRecord(new RollbackKey(id, tenantId, incidentId, status, 0, 1));
  }

  static RollbackPlanRecord rollbackPlanRecord(RollbackKey key) {
    return new RollbackPlanRecord(
        key.id(),
        key.tenantId(),
        key.incidentId(),
        "plan_1",
        "exec_1",
        key.status(),
        "high",
        "reason",
        key.requiredApprovals(),
        key.approvedCount(),
        0,
        "alice",
        null,
        null,
        null,
        null,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  record RollbackKey(
      String id,
      String tenantId,
      String incidentId,
      String status,
      int approvedCount,
      int requiredApprovals) {}

  static RollbackPlanStepRecord rollbackStep(
      String id, int order, String actionType, String targetType, String payload) {
    return new RollbackPlanStepRecord(
        id,
        "tenant_1",
        "rbp_1",
        "step_" + order,
        order,
        "step " + order,
        "desc",
        actionType,
        targetType,
        payload,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  static class FakeExecutionRepository extends FakeExecutionRepositoryBase {
    ExecutionRunRecord runRecord;
    final List<ExecutionStepRecord> steps = new ArrayList<>();
    final List<ExecutionRunCreateCommand> createdRuns = new ArrayList<>();
    final List<ExecutionStepCreateCommand> createdSteps = new ArrayList<>();

    @Override
    public Optional<ExecutionRunRecord> findRun(String tenantId, String executionId) {
      return Optional.ofNullable(runRecord);
    }

    @Override
    public List<ExecutionStepRecord> listExecutionSteps(String tenantId, String executionId) {
      return steps;
    }

    @Override
    public void createRun(ExecutionRunCreateCommand command) {
      createdRuns.add(command);
      runRecord = commandToRecord(command);
    }

    @Override
    public void createSteps(List<ExecutionStepCreateCommand> commands) {
      createdSteps.addAll(commands);
    }

    private static ExecutionRunRecord commandToRecord(ExecutionRunCreateCommand c) {
      return new ExecutionRunRecord(
          c.id(),
          c.tenantId(),
          c.incidentId(),
          c.planId(),
          c.status(),
          c.mode(),
          c.requestedBy(),
          null,
          null,
          null,
          null,
          null,
          c.attempt(),
          c.maxAttempts(),
          c.retryOfExecutionId(),
          null,
          null,
          c.timeoutSeconds(),
          c.approvalId(),
          c.approvalSnapshotJson(),
          c.planRiskLevel(),
          null,
          c.executionKind(),
          c.rollbackPlanId(),
          c.rollbackOfExecutionId(),
          OffsetDateTime.now(),
          OffsetDateTime.now());
    }
  }

  static class FakeRollbackRepository extends FakeExecutionRepositoryBase
      implements RollbackRepository {
    Optional<RollbackPlanRecord> planById = Optional.empty();
    final List<RollbackPlanCreateCommand> createdPlan = new ArrayList<>();
    final List<RollbackPlanStepCreateCommand> createdSteps = new ArrayList<>();
    final List<RollbackPlanStepRecord> steps = new ArrayList<>();
    String lastStatus;
    String lastSnapshot;
    boolean decisionExists;

    @Override
    public void createPlan(RollbackPlanCreateCommand command) {
      createdPlan.add(command);
      planById =
          Optional.of(
              new RollbackPlanRecord(
                  command.id(),
                  command.tenantId(),
                  command.incidentId(),
                  command.sourcePlanId(),
                  command.sourceExecutionId(),
                  command.status(),
                  command.riskLevel(),
                  command.reason(),
                  command.requiredApprovals(),
                  0,
                  0,
                  command.createdBy(),
                  null,
                  null,
                  null,
                  null,
                  OffsetDateTime.now(),
                  OffsetDateTime.now()));
    }

    @Override
    public void createStep(RollbackPlanStepCreateCommand command) {
      createdSteps.add(command);
      steps.add(
          new RollbackPlanStepRecord(
              command.id(),
              command.tenantId(),
              command.rollbackPlanId(),
              command.sourceStepId(),
              command.stepOrder(),
              command.title(),
              command.description(),
              command.actionType(),
              command.targetType(),
              command.actionPayloadJson(),
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public Optional<RollbackPlanRecord> findRollbackPlan(String tenantId, String rollbackPlanId) {
      return planById;
    }

    @Override
    public Optional<RollbackPlanRecord> findLatestRollbackPlanBySourceExecution(
        String tenantId, String sourceExecutionId) {
      return planById;
    }

    @Override
    public List<RollbackPlanStepRecord> listSteps(String tenantId, String rollbackPlanId) {
      return steps;
    }

    @Override
    public List<RollbackDecisionRecord> listDecisions(String tenantId, String rollbackPlanId) {
      return List.of();
    }

    @Override
    public boolean updatePlanStatus(
        String tenantId, String rollbackPlanId, String fromStatus, String toStatus) {
      lastStatus = toStatus;
      planById =
          planById.map(
              p ->
                  new RollbackPlanRecord(
                      p.id(),
                      p.tenantId(),
                      p.incidentId(),
                      p.sourcePlanId(),
                      p.sourceExecutionId(),
                      toStatus,
                      p.riskLevel(),
                      p.reason(),
                      p.requiredApprovals(),
                      p.approvedCount(),
                      p.rejectedCount(),
                      p.createdBy(),
                      p.submittedBy(),
                      p.submittedAt(),
                      p.decidedAt(),
                      p.approvalSnapshotJson(),
                      p.createdAt(),
                      p.updatedAt()));
      return true;
    }

    @Override
    public boolean submitPlan(String tenantId, String rollbackPlanId, String submittedBy) {
      lastStatus = "pending_approval";
      return true;
    }

    @Override
    public void createDecision(RollbackDecisionCreateCommand command) {}

    @Override
    public boolean decisionExists(String tenantId, String rollbackPlanId, String reviewer) {
      return decisionExists;
    }

    @Override
    public boolean markApproved(
        String tenantId, String rollbackPlanId, String approvalSnapshotJson) {
      lastStatus = "approved";
      lastSnapshot = approvalSnapshotJson;
      return true;
    }

    @Override
    public boolean markRejected(
        String tenantId, String rollbackPlanId, String approvalSnapshotJson) {
      lastStatus = "rejected";
      lastSnapshot = approvalSnapshotJson;
      return true;
    }

    @Override
    public boolean markExecuting(String tenantId, String rollbackPlanId) {
      lastStatus = "executing";
      return true;
    }

    @Override
    public boolean markSucceeded(String tenantId, String rollbackPlanId) {
      lastStatus = "succeeded";
      return true;
    }

    @Override
    public boolean markFailed(String tenantId, String rollbackPlanId) {
      lastStatus = "failed";
      return true;
    }
  }

  static class FakeExecutionRequestService extends ExecutionRequestService {
    private final FakeExecutionRepository repository;

    FakeExecutionRequestService(FakeExecutionRepository repository) {
      super(repository, new ExecutionProperties(), new ObjectMapper());
      this.repository = repository;
    }

    @Override
    public ExecutionRunResponse getExecution(String tenantId, String executionId) {
      ExecutionRunRecord r = repository.runRecord;
      return new ExecutionRunResponse(
          r.id(),
          r.tenantId(),
          r.incidentId(),
          r.planId(),
          r.status(),
          r.mode(),
          r.requestedBy(),
          r.runnerId(),
          r.errorMessage(),
          r.summary(),
          r.attempt(),
          r.maxAttempts(),
          r.retryOfExecutionId(),
          r.leaseUntil(),
          r.heartbeatAt(),
          r.timeoutSeconds(),
          r.approvalId(),
          r.approvalSnapshotJson(),
          r.planRiskLevel(),
          r.liveGuardPassedAt(),
          r.executionKind(),
          r.rollbackPlanId(),
          r.rollbackOfExecutionId(),
          List.<ExecutionStepResponse>of(),
          List.<ExecutionArtifactResponse>of(),
          r.startedAt(),
          r.finishedAt(),
          r.createdAt(),
          r.updatedAt());
    }
  }
}
