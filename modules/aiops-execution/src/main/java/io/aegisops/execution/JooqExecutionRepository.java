package io.aegisops.execution;

import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.Tables.AUTOMATION_PLAN;
import static io.aegisops.persistence.jooq.Tables.AUTOMATION_PLAN_STEP;
import static io.aegisops.persistence.jooq.Tables.EXECUTION_RUN;
import static io.aegisops.persistence.jooq.Tables.EXECUTION_STEP;
import static io.aegisops.persistence.jooq.Tables.INCIDENT_TIMELINE;

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
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqExecutionRepository implements ExecutionRepository {
  private final DSLContext dsl;

  public JooqExecutionRepository(DSLContext dsl) {
    this.dsl = dsl;
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
        .fetchOptional(this::toPlanRecord);
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
        .fetch(this::toPlanStepRecord);
  }

  @Override
  public Optional<ExecutionRunRecord> findLatestRunByPlan(String tenantId, String planId) {
    return selectRun()
        .where(EXECUTION_RUN.TENANT_ID.eq(tenantId))
        .and(EXECUTION_RUN.PLAN_ID.eq(planId))
        .orderBy(EXECUTION_RUN.CREATED_AT.desc())
        .limit(1)
        .fetchOptional(this::toRunRecord);
  }

  @Override
  public Optional<ExecutionRunRecord> findRun(String tenantId, String executionId) {
    return selectRun()
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
            EXECUTION_STEP.CREATED_AT,
            EXECUTION_STEP.UPDATED_AT)
        .from(EXECUTION_STEP)
        .where(EXECUTION_STEP.TENANT_ID.eq(tenantId))
        .and(EXECUTION_STEP.EXECUTION_ID.eq(executionId))
        .orderBy(EXECUTION_STEP.SEQUENCE_NO.asc())
        .fetch(this::toStepRecord);
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
          .set(EXECUTION_STEP.CREATED_AT, DSL.currentOffsetDateTime())
          .set(EXECUTION_STEP.UPDATED_AT, DSL.currentOffsetDateTime())
          .execute();
    }
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
  public Optional<ExecutionRunRecord> claimNextQueuedRun(String runnerId) {
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
            return Optional.<ExecutionRunRecord>empty();
          }

          int updated =
              tx.update(EXECUTION_RUN)
                  .set(EXECUTION_RUN.STATUS, "running")
                  .set(EXECUTION_RUN.RUNNER_ID, runnerId)
                  .set(EXECUTION_RUN.STARTED_AT, DSL.currentOffsetDateTime())
                  .set(EXECUTION_RUN.UPDATED_AT, DSL.currentOffsetDateTime())
                  .where(EXECUTION_RUN.ID.eq(id.get()))
                  .and(EXECUTION_RUN.STATUS.eq("queued"))
                  .execute();

          if (updated == 0) {
            return Optional.<ExecutionRunRecord>empty();
          }

          return selectRun(tx)
              .where(EXECUTION_RUN.ID.eq(id.get()))
              .fetchOptional(this::toRunRecord);
        });
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

  private SelectJoinStep<? extends org.jooq.Record> selectRun() {
    return selectRun(dsl);
  }

  private SelectJoinStep<? extends org.jooq.Record> selectRun(DSLContext context) {
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
            EXECUTION_RUN.CREATED_AT,
            EXECUTION_RUN.UPDATED_AT)
        .from(EXECUTION_RUN);
  }

  private PlanForExecutionRecord toPlanRecord(Record record) {
    return new PlanForExecutionRecord(
        record.get(AUTOMATION_PLAN.ID),
        record.get(AUTOMATION_PLAN.TENANT_ID),
        record.get(AUTOMATION_PLAN.INCIDENT_ID),
        record.get(AUTOMATION_PLAN.STATUS),
        record.get(AUTOMATION_PLAN.RISK_LEVEL),
        record.get(AUTOMATION_PLAN.TITLE),
        record.get(AUTOMATION_PLAN.SUMMARY));
  }

  private PlanStepForExecutionRecord toPlanStepRecord(Record record) {
    return new PlanStepForExecutionRecord(
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
        record.get(AUTOMATION_PLAN_STEP.STATUS));
  }

  private ExecutionRunRecord toRunRecord(Record record) {
    return new ExecutionRunRecord(
        record.get(EXECUTION_RUN.ID),
        record.get(EXECUTION_RUN.TENANT_ID),
        record.get(EXECUTION_RUN.INCIDENT_ID),
        record.get(EXECUTION_RUN.PLAN_ID),
        record.get(EXECUTION_RUN.STATUS),
        record.get(EXECUTION_RUN.MODE),
        record.get(EXECUTION_RUN.REQUESTED_BY),
        record.get(EXECUTION_RUN.RUNNER_ID),
        record.get(EXECUTION_RUN.STARTED_AT),
        record.get(EXECUTION_RUN.FINISHED_AT),
        record.get(EXECUTION_RUN.ERROR_MESSAGE),
        record.get(EXECUTION_RUN.SUMMARY),
        record.get(EXECUTION_RUN.CREATED_AT),
        record.get(EXECUTION_RUN.UPDATED_AT));
  }

  private ExecutionStepRecord toStepRecord(Record record) {
    return new ExecutionStepRecord(
        record.get(EXECUTION_STEP.ID),
        record.get(EXECUTION_STEP.TENANT_ID),
        record.get(EXECUTION_STEP.EXECUTION_ID),
        record.get(EXECUTION_STEP.PLAN_STEP_ID),
        value(record.get(EXECUTION_STEP.SEQUENCE_NO)),
        record.get(EXECUTION_STEP.NAME),
        record.get(EXECUTION_STEP.ACTION_TYPE),
        record.get(EXECUTION_STEP.TARGET_TYPE),
        record.get(EXECUTION_STEP.STATUS),
        record.get("action_payload_json", String.class),
        record.get(EXECUTION_STEP.COMMAND_SNAPSHOT),
        record.get(EXECUTION_STEP.OUTPUT),
        record.get(EXECUTION_STEP.ERROR_MESSAGE),
        record.get(EXECUTION_STEP.STARTED_AT),
        record.get(EXECUTION_STEP.FINISHED_AT),
        record.get(EXECUTION_STEP.CREATED_AT),
        record.get(EXECUTION_STEP.UPDATED_AT));
  }

  private int value(Integer value) {
    return value == null ? 0 : value;
  }
}
