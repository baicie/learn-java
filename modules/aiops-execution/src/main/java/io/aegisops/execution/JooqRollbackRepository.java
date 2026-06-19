package io.aegisops.execution;

import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.Tables.ROLLBACK_DECISION;
import static io.aegisops.persistence.jooq.Tables.ROLLBACK_PLAN;
import static io.aegisops.persistence.jooq.Tables.ROLLBACK_PLAN_STEP;

import io.aegisops.execution.dto.RollbackDecisionCreateCommand;
import io.aegisops.execution.dto.RollbackDecisionRecord;
import io.aegisops.execution.dto.RollbackPlanCreateCommand;
import io.aegisops.execution.dto.RollbackPlanRecord;
import io.aegisops.execution.dto.RollbackPlanStepCreateCommand;
import io.aegisops.execution.dto.RollbackPlanStepRecord;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqRollbackRepository implements RollbackRepository {
  private final DSLContext dsl;

  public JooqRollbackRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void createPlan(RollbackPlanCreateCommand command) {
    dsl.insertInto(ROLLBACK_PLAN)
        .set(ROLLBACK_PLAN.ID, command.id())
        .set(ROLLBACK_PLAN.TENANT_ID, command.tenantId())
        .set(ROLLBACK_PLAN.INCIDENT_ID, command.incidentId())
        .set(ROLLBACK_PLAN.SOURCE_PLAN_ID, command.sourcePlanId())
        .set(ROLLBACK_PLAN.SOURCE_EXECUTION_ID, command.sourceExecutionId())
        .set(ROLLBACK_PLAN.STATUS, command.status())
        .set(ROLLBACK_PLAN.RISK_LEVEL, command.riskLevel())
        .set(ROLLBACK_PLAN.REASON, command.reason())
        .set(ROLLBACK_PLAN.REQUIRED_APPROVALS, command.requiredApprovals())
        .set(ROLLBACK_PLAN.CREATED_BY, command.createdBy())
        .set(ROLLBACK_PLAN.CREATED_AT, DSL.currentOffsetDateTime())
        .set(ROLLBACK_PLAN.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void createStep(RollbackPlanStepCreateCommand command) {
    dsl.insertInto(ROLLBACK_PLAN_STEP)
        .set(ROLLBACK_PLAN_STEP.ID, command.id())
        .set(ROLLBACK_PLAN_STEP.TENANT_ID, command.tenantId())
        .set(ROLLBACK_PLAN_STEP.ROLLBACK_PLAN_ID, command.rollbackPlanId())
        .set(ROLLBACK_PLAN_STEP.SOURCE_STEP_ID, command.sourceStepId())
        .set(ROLLBACK_PLAN_STEP.STEP_ORDER, command.stepOrder())
        .set(ROLLBACK_PLAN_STEP.TITLE, command.title())
        .set(ROLLBACK_PLAN_STEP.DESCRIPTION, command.description())
        .set(ROLLBACK_PLAN_STEP.ACTION_TYPE, command.actionType())
        .set(ROLLBACK_PLAN_STEP.TARGET_TYPE, command.targetType())
        .set(ROLLBACK_PLAN_STEP.ACTION_PAYLOAD, jsonbValue(command.actionPayloadJson()))
        .set(ROLLBACK_PLAN_STEP.CREATED_AT, DSL.currentOffsetDateTime())
        .set(ROLLBACK_PLAN_STEP.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public Optional<RollbackPlanRecord> findRollbackPlan(String tenantId, String rollbackPlanId) {
    return selectPlan()
        .where(ROLLBACK_PLAN.TENANT_ID.eq(tenantId))
        .and(ROLLBACK_PLAN.ID.eq(rollbackPlanId))
        .fetchOptional(this::toPlanRecord);
  }

  @Override
  public Optional<RollbackPlanRecord> findLatestRollbackPlanBySourceExecution(
      String tenantId, String sourceExecutionId) {
    return selectPlan()
        .where(ROLLBACK_PLAN.TENANT_ID.eq(tenantId))
        .and(ROLLBACK_PLAN.SOURCE_EXECUTION_ID.eq(sourceExecutionId))
        .orderBy(ROLLBACK_PLAN.CREATED_AT.desc())
        .limit(1)
        .fetchOptional(this::toPlanRecord);
  }

  @Override
  public List<RollbackPlanStepRecord> listSteps(String tenantId, String rollbackPlanId) {
    return dsl.select(
            ROLLBACK_PLAN_STEP.ID,
            ROLLBACK_PLAN_STEP.TENANT_ID,
            ROLLBACK_PLAN_STEP.ROLLBACK_PLAN_ID,
            ROLLBACK_PLAN_STEP.SOURCE_STEP_ID,
            ROLLBACK_PLAN_STEP.STEP_ORDER,
            ROLLBACK_PLAN_STEP.TITLE,
            ROLLBACK_PLAN_STEP.DESCRIPTION,
            ROLLBACK_PLAN_STEP.ACTION_TYPE,
            ROLLBACK_PLAN_STEP.TARGET_TYPE,
            ROLLBACK_PLAN_STEP.ACTION_PAYLOAD.cast(String.class).as("action_payload_json"),
            ROLLBACK_PLAN_STEP.CREATED_AT,
            ROLLBACK_PLAN_STEP.UPDATED_AT)
        .from(ROLLBACK_PLAN_STEP)
        .where(ROLLBACK_PLAN_STEP.TENANT_ID.eq(tenantId))
        .and(ROLLBACK_PLAN_STEP.ROLLBACK_PLAN_ID.eq(rollbackPlanId))
        .orderBy(ROLLBACK_PLAN_STEP.STEP_ORDER.asc())
        .fetch(this::toStepRecord);
  }

  @Override
  public List<RollbackDecisionRecord> listDecisions(String tenantId, String rollbackPlanId) {
    return dsl.select(
            ROLLBACK_DECISION.ID,
            ROLLBACK_DECISION.TENANT_ID,
            ROLLBACK_DECISION.ROLLBACK_PLAN_ID,
            ROLLBACK_DECISION.REVIEWER,
            ROLLBACK_DECISION.DECISION,
            ROLLBACK_DECISION.COMMENT,
            ROLLBACK_DECISION.CREATED_AT)
        .from(ROLLBACK_DECISION)
        .where(ROLLBACK_DECISION.TENANT_ID.eq(tenantId))
        .and(ROLLBACK_DECISION.ROLLBACK_PLAN_ID.eq(rollbackPlanId))
        .orderBy(ROLLBACK_DECISION.CREATED_AT.asc())
        .fetch(this::toDecisionRecord);
  }

  @Override
  public boolean updatePlanStatus(
      String tenantId, String rollbackPlanId, String fromStatus, String toStatus) {
    return dsl.update(ROLLBACK_PLAN)
            .set(ROLLBACK_PLAN.STATUS, toStatus)
            .set(ROLLBACK_PLAN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(ROLLBACK_PLAN.TENANT_ID.eq(tenantId))
            .and(ROLLBACK_PLAN.ID.eq(rollbackPlanId))
            .and(ROLLBACK_PLAN.STATUS.eq(fromStatus))
            .execute()
        > 0;
  }

  @Override
  public boolean submitPlan(String tenantId, String rollbackPlanId, String submittedBy) {
    return dsl.update(ROLLBACK_PLAN)
            .set(ROLLBACK_PLAN.STATUS, "pending_approval")
            .set(ROLLBACK_PLAN.SUBMITTED_BY, submittedBy)
            .set(ROLLBACK_PLAN.SUBMITTED_AT, DSL.currentOffsetDateTime())
            .set(ROLLBACK_PLAN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(ROLLBACK_PLAN.TENANT_ID.eq(tenantId))
            .and(ROLLBACK_PLAN.ID.eq(rollbackPlanId))
            .and(ROLLBACK_PLAN.STATUS.eq("draft"))
            .execute()
        > 0;
  }

  @Override
  public void createDecision(RollbackDecisionCreateCommand command) {
    dsl.insertInto(ROLLBACK_DECISION)
        .set(ROLLBACK_DECISION.ID, command.id())
        .set(ROLLBACK_DECISION.TENANT_ID, command.tenantId())
        .set(ROLLBACK_DECISION.ROLLBACK_PLAN_ID, command.rollbackPlanId())
        .set(ROLLBACK_DECISION.REVIEWER, command.reviewer())
        .set(ROLLBACK_DECISION.DECISION, command.decision())
        .set(ROLLBACK_DECISION.COMMENT, command.comment())
        .set(ROLLBACK_DECISION.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public boolean decisionExists(String tenantId, String rollbackPlanId, String reviewer) {
    return dsl.fetchExists(
        ROLLBACK_DECISION,
        ROLLBACK_DECISION
            .TENANT_ID
            .eq(tenantId)
            .and(ROLLBACK_DECISION.ROLLBACK_PLAN_ID.eq(rollbackPlanId))
            .and(ROLLBACK_DECISION.REVIEWER.eq(reviewer)));
  }

  @Override
  public int countDecisions(String tenantId, String rollbackPlanId, String decision) {
    return dsl.fetchCount(
        dsl.selectFrom(ROLLBACK_DECISION)
            .where(ROLLBACK_DECISION.TENANT_ID.eq(tenantId))
            .and(ROLLBACK_DECISION.ROLLBACK_PLAN_ID.eq(rollbackPlanId))
            .and(ROLLBACK_DECISION.DECISION.eq(decision)));
  }

  @Override
  public boolean markApproved(
      String tenantId, String rollbackPlanId, int approvedCount, String approvalSnapshotJson) {
    return dsl.update(ROLLBACK_PLAN)
            .set(ROLLBACK_PLAN.STATUS, "approved")
            .set(ROLLBACK_PLAN.APPROVED_COUNT, approvedCount)
            .set(ROLLBACK_PLAN.APPROVAL_SNAPSHOT, jsonbValue(approvalSnapshotJson))
            .set(ROLLBACK_PLAN.DECIDED_AT, DSL.currentOffsetDateTime())
            .set(ROLLBACK_PLAN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(ROLLBACK_PLAN.TENANT_ID.eq(tenantId))
            .and(ROLLBACK_PLAN.ID.eq(rollbackPlanId))
            .and(ROLLBACK_PLAN.STATUS.eq("pending_approval"))
            .execute()
        > 0;
  }

  @Override
  public boolean markRejected(
      String tenantId, String rollbackPlanId, int rejectedCount, String approvalSnapshotJson) {
    return dsl.update(ROLLBACK_PLAN)
            .set(ROLLBACK_PLAN.STATUS, "rejected")
            .set(ROLLBACK_PLAN.REJECTED_COUNT, rejectedCount)
            .set(ROLLBACK_PLAN.APPROVAL_SNAPSHOT, jsonbValue(approvalSnapshotJson))
            .set(ROLLBACK_PLAN.DECIDED_AT, DSL.currentOffsetDateTime())
            .set(ROLLBACK_PLAN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(ROLLBACK_PLAN.TENANT_ID.eq(tenantId))
            .and(ROLLBACK_PLAN.ID.eq(rollbackPlanId))
            .and(ROLLBACK_PLAN.STATUS.eq("pending_approval"))
            .execute()
        > 0;
  }

  @Override
  public boolean updatePlanStatusToCancelled(String tenantId, String rollbackPlanId) {
    return dsl.update(ROLLBACK_PLAN)
            .set(ROLLBACK_PLAN.STATUS, "cancelled")
            .set(ROLLBACK_PLAN.DECIDED_AT, DSL.currentOffsetDateTime())
            .set(ROLLBACK_PLAN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(ROLLBACK_PLAN.TENANT_ID.eq(tenantId))
            .and(ROLLBACK_PLAN.ID.eq(rollbackPlanId))
            .and(ROLLBACK_PLAN.STATUS.eq("pending_approval"))
            .execute()
        > 0;
  }

  @Override
  public boolean markCancelled(String tenantId, String rollbackPlanId) {
    return dsl.update(ROLLBACK_PLAN)
            .set(ROLLBACK_PLAN.STATUS, "cancelled")
            .set(ROLLBACK_PLAN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(ROLLBACK_PLAN.TENANT_ID.eq(tenantId))
            .and(ROLLBACK_PLAN.ID.eq(rollbackPlanId))
            .execute()
        > 0;
  }

  @Override
  public boolean markExecuting(String tenantId, String rollbackPlanId) {
    return updatePlanStatus(tenantId, rollbackPlanId, "approved", "executing");
  }

  @Override
  public boolean markSucceeded(String tenantId, String rollbackPlanId) {
    return updatePlanStatus(tenantId, rollbackPlanId, "executing", "succeeded");
  }

  @Override
  public boolean markFailed(String tenantId, String rollbackPlanId) {
    return updatePlanStatus(tenantId, rollbackPlanId, "executing", "failed");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private SelectJoinStep<Record> selectPlan() {
    return (SelectJoinStep)
        dsl.select(
                ROLLBACK_PLAN.ID,
                ROLLBACK_PLAN.TENANT_ID,
                ROLLBACK_PLAN.INCIDENT_ID,
                ROLLBACK_PLAN.SOURCE_PLAN_ID,
                ROLLBACK_PLAN.SOURCE_EXECUTION_ID,
                ROLLBACK_PLAN.STATUS,
                ROLLBACK_PLAN.RISK_LEVEL,
                ROLLBACK_PLAN.REASON,
                ROLLBACK_PLAN.REQUIRED_APPROVALS,
                ROLLBACK_PLAN.APPROVED_COUNT,
                ROLLBACK_PLAN.REJECTED_COUNT,
                ROLLBACK_PLAN.CREATED_BY,
                ROLLBACK_PLAN.SUBMITTED_BY,
                ROLLBACK_PLAN.SUBMITTED_AT,
                ROLLBACK_PLAN.DECIDED_AT,
                ROLLBACK_PLAN.APPROVAL_SNAPSHOT.cast(String.class).as("approval_snapshot_json"),
                ROLLBACK_PLAN.CREATED_AT,
                ROLLBACK_PLAN.UPDATED_AT)
            .from(ROLLBACK_PLAN);
  }

  private RollbackPlanRecord toPlanRecord(Record record) {
    return new RollbackPlanRecord(
        record.get(ROLLBACK_PLAN.ID),
        record.get(ROLLBACK_PLAN.TENANT_ID),
        record.get(ROLLBACK_PLAN.INCIDENT_ID),
        record.get(ROLLBACK_PLAN.SOURCE_PLAN_ID),
        record.get(ROLLBACK_PLAN.SOURCE_EXECUTION_ID),
        record.get(ROLLBACK_PLAN.STATUS),
        record.get(ROLLBACK_PLAN.RISK_LEVEL),
        record.get(ROLLBACK_PLAN.REASON),
        value(record.get(ROLLBACK_PLAN.REQUIRED_APPROVALS)),
        value(record.get(ROLLBACK_PLAN.APPROVED_COUNT)),
        value(record.get(ROLLBACK_PLAN.REJECTED_COUNT)),
        record.get(ROLLBACK_PLAN.CREATED_BY),
        record.get(ROLLBACK_PLAN.SUBMITTED_BY),
        record.get(ROLLBACK_PLAN.SUBMITTED_AT),
        record.get(ROLLBACK_PLAN.DECIDED_AT),
        record.get("approval_snapshot_json", String.class),
        record.get(ROLLBACK_PLAN.CREATED_AT),
        record.get(ROLLBACK_PLAN.UPDATED_AT));
  }

  private RollbackPlanStepRecord toStepRecord(Record record) {
    return new RollbackPlanStepRecord(
        record.get(ROLLBACK_PLAN_STEP.ID),
        record.get(ROLLBACK_PLAN_STEP.TENANT_ID),
        record.get(ROLLBACK_PLAN_STEP.ROLLBACK_PLAN_ID),
        record.get(ROLLBACK_PLAN_STEP.SOURCE_STEP_ID),
        value(record.get(ROLLBACK_PLAN_STEP.STEP_ORDER)),
        record.get(ROLLBACK_PLAN_STEP.TITLE),
        record.get(ROLLBACK_PLAN_STEP.DESCRIPTION),
        record.get(ROLLBACK_PLAN_STEP.ACTION_TYPE),
        record.get(ROLLBACK_PLAN_STEP.TARGET_TYPE),
        record.get("action_payload_json", String.class),
        record.get(ROLLBACK_PLAN_STEP.CREATED_AT),
        record.get(ROLLBACK_PLAN_STEP.UPDATED_AT));
  }

  private RollbackDecisionRecord toDecisionRecord(Record record) {
    return new RollbackDecisionRecord(
        record.get(ROLLBACK_DECISION.ID),
        record.get(ROLLBACK_DECISION.TENANT_ID),
        record.get(ROLLBACK_DECISION.ROLLBACK_PLAN_ID),
        record.get(ROLLBACK_DECISION.REVIEWER),
        record.get(ROLLBACK_DECISION.DECISION),
        record.get(ROLLBACK_DECISION.COMMENT),
        record.get(ROLLBACK_DECISION.CREATED_AT));
  }

  private int value(Integer value) {
    return value == null ? 0 : value;
  }
}
