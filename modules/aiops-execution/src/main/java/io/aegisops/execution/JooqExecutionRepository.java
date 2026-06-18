package io.aegisops.execution;

import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.Tables.AUTOMATION_APPROVAL;
import static io.aegisops.persistence.jooq.Tables.AUTOMATION_PLAN;
import static io.aegisops.persistence.jooq.Tables.AUTOMATION_PLAN_STEP;
import static io.aegisops.persistence.jooq.Tables.EXECUTION_RUN;
import static io.aegisops.persistence.jooq.Tables.EXECUTION_STEP;
import static io.aegisops.persistence.jooq.Tables.INCIDENT_TIMELINE;

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
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqExecutionRepository implements ExecutionRepository {
  private final DSLContext dsl;
  private final JooqExecutionArtifactQueries artifactQueries;

  public JooqExecutionRepository(DSLContext dsl) {
    this.dsl = dsl;
    this.artifactQueries = new JooqExecutionArtifactQueries(dsl);
  }

  @Override
  public Optional<PlanForExecutionRecord> findPlan(String tenantId, String planId) {
    return dsl.select(
            AUTOMATION_PLAN.ID,
            AUTOMATION_PLAN.TENANT_ID,
            AUTOMATION_PLAN.INCIDENT_ID,
            AUTOMATION_PLAN.STATUS,
            AUTOMATION_PLAN.RISK_LEVEL,
            AUTOMATION_PLAN.TITLE,
            AUTOMATION_PLAN.SUMMARY)
        .from(AUTOMATION_PLAN)
        .where(AUTOMATION_PLAN.TENANT_ID.eq(tenantId))
        .and(AUTOMATION_PLAN.ID.eq(planId))
        .fetchOptional(
            record ->
                new PlanForExecutionRecord(
                    record.get(AUTOMATION_PLAN.ID),
                    record.get(AUTOMATION_PLAN.TENANT_ID),
                    record.get(AUTOMATION_PLAN.INCIDENT_ID),
                    record.get(AUTOMATION_PLAN.STATUS),
                    record.get(AUTOMATION_PLAN.RISK_LEVEL),
                    record.get(AUTOMATION_PLAN.TITLE),
                    record.get(AUTOMATION_PLAN.SUMMARY)));
  }

  @Override
  public List<PlanStepForExecutionRecord> listPlanSteps(String planId) {
    return dsl.select(
            AUTOMATION_PLAN_STEP.ID,
            AUTOMATION_PLAN_STEP.PLAN_ID,
            AUTOMATION_PLAN_STEP.SEQUENCE_NO,
            AUTOMATION_PLAN_STEP.NAME,
            AUTOMATION_PLAN_STEP.ACTION_TYPE,
            AUTOMATION_PLAN_STEP.TARGET_TYPE,
            AUTOMATION_PLAN_STEP.ACTION_PAYLOAD.cast(String.class).as("action_payload_json"),
            AUTOMATION_PLAN_STEP.DESCRIPTION,
            AUTOMATION_PLAN_STEP.EXPECTED_RESULT,
            AUTOMATION_PLAN_STEP.ROLLBACK_HINT,
            AUTOMATION_PLAN_STEP.REQUIRES_APPROVAL,
            AUTOMATION_PLAN_STEP.STATUS)
        .from(AUTOMATION_PLAN_STEP)
        .where(AUTOMATION_PLAN_STEP.PLAN_ID.eq(planId))
        .orderBy(AUTOMATION_PLAN_STEP.SEQUENCE_NO.asc())
        .fetch(
            record ->
                new PlanStepForExecutionRecord(
                    record.get(AUTOMATION_PLAN_STEP.ID),
                    record.get(AUTOMATION_PLAN_STEP.PLAN_ID),
                    value(record.get(AUTOMATION_PLAN_STEP.SEQUENCE_NO)),
                    record.get(AUTOMATION_PLAN_STEP.NAME),
                    record.get(AUTOMATION_PLAN_STEP.ACTION_TYPE),
                    record.get(AUTOMATION_PLAN_STEP.TARGET_TYPE),
                    record.get("action_payload_json", String.class),
                    record.get(AUTOMATION_PLAN_STEP.DESCRIPTION),
                    record.get(AUTOMATION_PLAN_STEP.EXPECTED_RESULT),
                    record.get(AUTOMATION_PLAN_STEP.ROLLBACK_HINT),
                    Boolean.TRUE.equals(record.get(AUTOMATION_PLAN_STEP.REQUIRES_APPROVAL)),
                    record.get(AUTOMATION_PLAN_STEP.STATUS)));
  }

  @Override
  public Optional<ExecutionRunRecord> findLatestRunByPlan(String tenantId, String planId) {
    return selectRun(dsl)
        .where(EXECUTION_RUN.TENANT_ID.eq(tenantId))
        .and(EXECUTION_RUN.PLAN_ID.eq(planId))
        .orderBy(EXECUTION_RUN.CREATED_AT.desc())
        .limit(1)
        .fetchOptional(this::toRunRecord);
  }

  @Override
  public Optional<ExecutionRunRecord> findRun(String tenantId, String executionId) {
    return selectRun(dsl)
        .where(EXECUTION_RUN.TENANT_ID.eq(tenantId))
        .and(EXECUTION_RUN.ID.eq(executionId))
        .fetchOptional(this::toRunRecord);
  }

  @Override
  public List<ExecutionStepRecord> listExecutionSteps(String tenantId, String executionId) {
    return dsl.select(
            EXECUTION_STEP.ID,
            EXECUTION_STEP.TENANT_ID,
            EXECUTION_STEP.EXECUTION_ID,
            EXECUTION_STEP.PLAN_STEP_ID,
            EXECUTION_STEP.SEQUENCE_NO,
            EXECUTION_STEP.NAME,
            EXECUTION_STEP.ACTION_TYPE,
            EXECUTION_STEP.TARGET_TYPE,
            EXECUTION_STEP.STATUS,
            EXECUTION_STEP.ACTION_PAYLOAD.cast(String.class).as("action_payload_json"),
            EXECUTION_STEP.COMMAND_SNAPSHOT,
            EXECUTION_STEP.OUTPUT,
            EXECUTION_STEP.ERROR_MESSAGE,
            EXECUTION_STEP.STARTED_AT,
            EXECUTION_STEP.FINISHED_AT,
            EXECUTION_STEP.ATTEMPT,
            EXECUTION_STEP.TIMEOUT_SECONDS,
            EXECUTION_STEP.ARTIFACT_COUNT,
            EXECUTION_STEP.CREATED_AT,
            EXECUTION_STEP.UPDATED_AT)
        .from(EXECUTION_STEP)
        .where(EXECUTION_STEP.TENANT_ID.eq(tenantId))
        .and(EXECUTION_STEP.EXECUTION_ID.eq(executionId))
        .orderBy(EXECUTION_STEP.SEQUENCE_NO.asc())
        .fetch(this::toStepRecord);
  }

  @Override
  public List<ExecutionArtifactRecord> listArtifacts(String tenantId, String executionId) {
    return artifactQueries.listArtifacts(tenantId, executionId);
  }

  @Override
  public void createRun(ExecutionRunCreateCommand command) {
    dsl.insertInto(EXECUTION_RUN)
        .set(EXECUTION_RUN.ID, command.id())
        .set(EXECUTION_RUN.TENANT_ID, command.tenantId())
        .set(EXECUTION_RUN.INCIDENT_ID, command.incidentId())
        .set(EXECUTION_RUN.PLAN_ID, command.planId())
        .set(EXECUTION_RUN.STATUS, command.status())
        .set(EXECUTION_RUN.MODE, command.mode())
        .set(EXECUTION_RUN.REQUESTED_BY, command.requestedBy())
        .set(EXECUTION_RUN.ATTEMPT, command.attempt())
        .set(EXECUTION_RUN.MAX_ATTEMPTS, command.maxAttempts())
        .set(EXECUTION_RUN.RETRY_OF_EXECUTION_ID, command.retryOfExecutionId())
        .set(EXECUTION_RUN.TIMEOUT_SECONDS, command.timeoutSeconds())
        .set(EXECUTION_RUN.APPROVAL_ID, command.approvalId())
        .set(EXECUTION_RUN.APPROVAL_SNAPSHOT, jsonbValue(command.approvalSnapshotJson()))
        .set(EXECUTION_RUN.PLAN_RISK_LEVEL, command.planRiskLevel())
        .set(EXECUTION_RUN.CREATED_AT, DSL.currentOffsetDateTime())
        .set(EXECUTION_RUN.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void createSteps(List<ExecutionStepCreateCommand> commands) {
    for (ExecutionStepCreateCommand command : commands) {
      dsl.insertInto(EXECUTION_STEP)
          .set(EXECUTION_STEP.ID, command.id())
          .set(EXECUTION_STEP.TENANT_ID, command.tenantId())
          .set(EXECUTION_STEP.EXECUTION_ID, command.executionId())
          .set(EXECUTION_STEP.PLAN_STEP_ID, command.planStepId())
          .set(EXECUTION_STEP.SEQUENCE_NO, command.sequenceNo())
          .set(EXECUTION_STEP.NAME, command.name())
          .set(EXECUTION_STEP.ACTION_TYPE, command.actionType())
          .set(EXECUTION_STEP.TARGET_TYPE, command.targetType())
          .set(EXECUTION_STEP.STATUS, command.status())
          .set(EXECUTION_STEP.ACTION_PAYLOAD, jsonbValue(command.actionPayloadJson()))
          .set(EXECUTION_STEP.COMMAND_SNAPSHOT, command.commandSnapshot())
          .set(EXECUTION_STEP.ATTEMPT, command.attempt())
          .set(EXECUTION_STEP.TIMEOUT_SECONDS, command.timeoutSeconds())
          .set(EXECUTION_STEP.CREATED_AT, DSL.currentOffsetDateTime())
          .set(EXECUTION_STEP.UPDATED_AT, DSL.currentOffsetDateTime())
          .execute();
    }
  }

  @Override
  public void createArtifact(ExecutionArtifactCreateCommand command) {
    artifactQueries.createArtifact(command);
  }

  @Override
  public boolean incrementStepArtifactCount(String tenantId, String stepId) {
    return artifactQueries.incrementStepArtifactCount(tenantId, stepId);
  }

  @Override
  public boolean updatePlanStatus(String tenantId, String planId, String status) {
    return dsl.update(AUTOMATION_PLAN)
            .set(AUTOMATION_PLAN.STATUS, status)
            .set(AUTOMATION_PLAN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(AUTOMATION_PLAN.TENANT_ID.eq(tenantId))
            .and(AUTOMATION_PLAN.ID.eq(planId))
            .execute()
        > 0;
  }

  @Override
  public boolean cancelRun(String tenantId, String executionId) {
    return dsl.update(EXECUTION_RUN)
            .set(EXECUTION_RUN.STATUS, "cancelled")
            .set(EXECUTION_RUN.FINISHED_AT, DSL.currentOffsetDateTime())
            .set(EXECUTION_RUN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(EXECUTION_RUN.TENANT_ID.eq(tenantId))
            .and(EXECUTION_RUN.ID.eq(executionId))
            .and(EXECUTION_RUN.STATUS.in("queued", "running"))
            .execute()
        > 0;
  }

  @Override
  public boolean cancelExecutionSteps(String tenantId, String executionId) {
    dsl.update(EXECUTION_STEP)
        .set(EXECUTION_STEP.STATUS, "cancelled")
        .set(EXECUTION_STEP.FINISHED_AT, DSL.currentOffsetDateTime())
        .set(EXECUTION_STEP.ERROR_MESSAGE, "Execution was cancelled.")
        .set(EXECUTION_STEP.UPDATED_AT, DSL.currentOffsetDateTime())
        .where(EXECUTION_STEP.TENANT_ID.eq(tenantId))
        .and(EXECUTION_STEP.EXECUTION_ID.eq(executionId))
        .and(EXECUTION_STEP.STATUS.in("queued", "running"))
        .execute();

    return true;
  }

  @Override
  public Optional<ExecutionRunRecord> claimNextQueuedRun(
      String runnerId, OffsetDateTime now, OffsetDateTime leaseUntil) {
    return dsl.transactionResult(
        config -> {
          DSLContext tx = DSL.using(config);

          Optional<String> id =
              tx.select(EXECUTION_RUN.ID)
                  .from(EXECUTION_RUN)
                  .where(EXECUTION_RUN.STATUS.eq("queued"))
                  .orderBy(EXECUTION_RUN.CREATED_AT.asc())
                  .limit(1)
                  .forUpdate()
                  .skipLocked()
                  .fetchOptional(EXECUTION_RUN.ID);

          if (id.isEmpty()) {
            return Optional.empty();
          }

          int updated =
              tx.update(EXECUTION_RUN)
                  .set(EXECUTION_RUN.STATUS, "running")
                  .set(EXECUTION_RUN.RUNNER_ID, runnerId)
                  .set(EXECUTION_RUN.STARTED_AT, now)
                  .set(EXECUTION_RUN.HEARTBEAT_AT, now)
                  .set(EXECUTION_RUN.LEASE_UNTIL, leaseUntil)
                  .set(EXECUTION_RUN.UPDATED_AT, DSL.currentOffsetDateTime())
                  .where(EXECUTION_RUN.ID.eq(id.get()))
                  .and(EXECUTION_RUN.STATUS.eq("queued"))
                  .execute();

          if (updated == 0) {
            return Optional.empty();
          }

          return selectRun(tx)
              .where(EXECUTION_RUN.ID.eq(id.get()))
              .fetchOptional(this::toRunRecord);
        });
  }

  @Override
  public boolean heartbeat(
      String tenantId,
      String executionId,
      String runnerId,
      OffsetDateTime heartbeatAt,
      OffsetDateTime leaseUntil) {
    return dsl.update(EXECUTION_RUN)
            .set(EXECUTION_RUN.HEARTBEAT_AT, heartbeatAt)
            .set(EXECUTION_RUN.LEASE_UNTIL, leaseUntil)
            .set(EXECUTION_RUN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(EXECUTION_RUN.TENANT_ID.eq(tenantId))
            .and(EXECUTION_RUN.ID.eq(executionId))
            .and(EXECUTION_RUN.RUNNER_ID.eq(runnerId))
            .and(EXECUTION_RUN.STATUS.eq("running"))
            .execute()
        > 0;
  }

  @Override
  public List<ExecutionRunRecord> findExpiredRunningRuns(OffsetDateTime now, int limit) {
    return selectRun(dsl)
        .where(EXECUTION_RUN.STATUS.eq("running"))
        .and(EXECUTION_RUN.LEASE_UNTIL.lt(now))
        .orderBy(EXECUTION_RUN.LEASE_UNTIL.asc())
        .limit(limit)
        .fetch(this::toRunRecord);
  }

  @Override
  public boolean timeoutRun(String tenantId, String executionId, String errorMessage) {
    return dsl.update(EXECUTION_RUN)
            .set(EXECUTION_RUN.STATUS, "timeout")
            .set(EXECUTION_RUN.FINISHED_AT, DSL.currentOffsetDateTime())
            .set(EXECUTION_RUN.ERROR_MESSAGE, errorMessage)
            .set(EXECUTION_RUN.SUMMARY, "Execution timed out.")
            .set(EXECUTION_RUN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(EXECUTION_RUN.TENANT_ID.eq(tenantId))
            .and(EXECUTION_RUN.ID.eq(executionId))
            .and(EXECUTION_RUN.STATUS.eq("running"))
            .execute()
        > 0;
  }

  @Override
  public boolean timeoutExecutionSteps(String tenantId, String executionId) {
    dsl.update(EXECUTION_STEP)
        .set(EXECUTION_STEP.STATUS, "timeout")
        .set(EXECUTION_STEP.FINISHED_AT, DSL.currentOffsetDateTime())
        .set(EXECUTION_STEP.ERROR_MESSAGE, "Execution lease timed out.")
        .set(EXECUTION_STEP.UPDATED_AT, DSL.currentOffsetDateTime())
        .where(EXECUTION_STEP.TENANT_ID.eq(tenantId))
        .and(EXECUTION_STEP.EXECUTION_ID.eq(executionId))
        .and(EXECUTION_STEP.STATUS.in("queued", "running"))
        .execute();

    return true;
  }

  @Override
  public boolean updateRunStatus(ExecutionRunStatusUpdateCommand command) {
    return dsl.update(EXECUTION_RUN)
            .set(EXECUTION_RUN.STATUS, command.status())
            .set(EXECUTION_RUN.RUNNER_ID, command.runnerId())
            .set(EXECUTION_RUN.STARTED_AT, command.startedAt())
            .set(EXECUTION_RUN.FINISHED_AT, command.finishedAt())
            .set(EXECUTION_RUN.ERROR_MESSAGE, command.errorMessage())
            .set(EXECUTION_RUN.SUMMARY, command.summary())
            .set(EXECUTION_RUN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(EXECUTION_RUN.TENANT_ID.eq(command.tenantId()))
            .and(EXECUTION_RUN.ID.eq(command.executionId()))
            .execute()
        > 0;
  }

  @Override
  public boolean updateStepStatus(ExecutionStepStatusUpdateCommand command) {
    return dsl.update(EXECUTION_STEP)
            .set(EXECUTION_STEP.STATUS, command.status())
            .set(EXECUTION_STEP.STARTED_AT, command.startedAt())
            .set(EXECUTION_STEP.FINISHED_AT, command.finishedAt())
            .set(EXECUTION_STEP.OUTPUT, command.output())
            .set(EXECUTION_STEP.ERROR_MESSAGE, command.errorMessage())
            .set(EXECUTION_STEP.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(EXECUTION_STEP.TENANT_ID.eq(command.tenantId()))
            .and(EXECUTION_STEP.ID.eq(command.stepId()))
            .execute()
        > 0;
  }

  @Override
  public void addTimeline(TimelineCreateCommand command) {
    dsl.insertInto(INCIDENT_TIMELINE)
        .set(INCIDENT_TIMELINE.ID, command.id())
        .set(INCIDENT_TIMELINE.INCIDENT_ID, command.incidentId())
        .set(INCIDENT_TIMELINE.EVENT_TIME, command.eventTime())
        .set(INCIDENT_TIMELINE.EVENT_TYPE, command.eventType())
        .set(INCIDENT_TIMELINE.TITLE, command.title())
        .set(INCIDENT_TIMELINE.DESCRIPTION, command.description())
        .set(INCIDENT_TIMELINE.SOURCE, command.source())
        .set(INCIDENT_TIMELINE.PAYLOAD, jsonbValue(command.payloadJson()))
        .execute();
  }

  @Override
  public Optional<ExecutionApprovalSnapshotRecord> findLatestApprovedApprovalSnapshot(
      String tenantId, String planId) {
    return dsl.select(
            AUTOMATION_APPROVAL.ID,
            AUTOMATION_APPROVAL.PLAN_ID,
            AUTOMATION_APPROVAL.STATUS,
            AUTOMATION_APPROVAL.SUBMITTED_BY,
            AUTOMATION_APPROVAL.REQUIRED_APPROVALS,
            AUTOMATION_APPROVAL.APPROVED_COUNT,
            AUTOMATION_APPROVAL.COMPLETED_AT)
        .from(AUTOMATION_APPROVAL)
        .where(AUTOMATION_APPROVAL.TENANT_ID.eq(tenantId))
        .and(AUTOMATION_APPROVAL.PLAN_ID.eq(planId))
        .and(AUTOMATION_APPROVAL.STATUS.eq("approved"))
        .orderBy(AUTOMATION_APPROVAL.COMPLETED_AT.desc())
        .limit(1)
        .fetchOptional(
            record ->
                new ExecutionApprovalSnapshotRecord(
                    record.get(AUTOMATION_APPROVAL.ID),
                    record.get(AUTOMATION_APPROVAL.PLAN_ID),
                    record.get(AUTOMATION_APPROVAL.STATUS),
                    record.get(AUTOMATION_APPROVAL.SUBMITTED_BY),
                    value(record.get(AUTOMATION_APPROVAL.REQUIRED_APPROVALS)),
                    value(record.get(AUTOMATION_APPROVAL.APPROVED_COUNT)),
                    record.get(AUTOMATION_APPROVAL.COMPLETED_AT)));
  }

  @Override
  public boolean markLiveGuardPassed(String tenantId, String executionId) {
    return dsl.update(EXECUTION_RUN)
            .set(EXECUTION_RUN.LIVE_GUARD_PASSED_AT, DSL.currentOffsetDateTime())
            .set(EXECUTION_RUN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(EXECUTION_RUN.TENANT_ID.eq(tenantId))
            .and(EXECUTION_RUN.ID.eq(executionId))
            .and(EXECUTION_RUN.MODE.eq("live"))
            .execute()
        > 0;
  }

  private org.jooq.SelectJoinStep<?> selectRun(DSLContext context) {
    return context
        .select(
            EXECUTION_RUN.ID,
            EXECUTION_RUN.TENANT_ID,
            EXECUTION_RUN.INCIDENT_ID,
            EXECUTION_RUN.PLAN_ID,
            EXECUTION_RUN.STATUS,
            EXECUTION_RUN.MODE,
            EXECUTION_RUN.REQUESTED_BY,
            EXECUTION_RUN.RUNNER_ID,
            EXECUTION_RUN.STARTED_AT,
            EXECUTION_RUN.FINISHED_AT,
            EXECUTION_RUN.ERROR_MESSAGE,
            EXECUTION_RUN.SUMMARY,
            EXECUTION_RUN.ATTEMPT,
            EXECUTION_RUN.MAX_ATTEMPTS,
            EXECUTION_RUN.RETRY_OF_EXECUTION_ID,
            EXECUTION_RUN.LEASE_UNTIL,
            EXECUTION_RUN.HEARTBEAT_AT,
            EXECUTION_RUN.TIMEOUT_SECONDS,
            EXECUTION_RUN.APPROVAL_ID,
            EXECUTION_RUN.APPROVAL_SNAPSHOT.cast(String.class).as("approval_snapshot_json"),
            EXECUTION_RUN.PLAN_RISK_LEVEL,
            EXECUTION_RUN.LIVE_GUARD_PASSED_AT,
            EXECUTION_RUN.CREATED_AT,
            EXECUTION_RUN.UPDATED_AT)
        .from(EXECUTION_RUN);
  }

  private ExecutionRunRecord toRunRecord(org.jooq.Record record) {
    return ExecutionRunRecordMapper.toRunRecord(record, this::value);
  }

  private ExecutionStepRecord toStepRecord(org.jooq.Record record) {
    return ExecutionStepAndArtifactMapper.toStepRecord(record);
  }

  private int value(Integer value) {
    return value == null ? 0 : value;
  }
}
