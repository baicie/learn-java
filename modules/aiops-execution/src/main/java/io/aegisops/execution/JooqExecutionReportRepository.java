package io.aegisops.execution;

import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.public_.Tables.EXECUTION_AUDIT_EVENT;
import static io.aegisops.persistence.jooq.public_.Tables.EXECUTION_REPORT;
import static io.aegisops.persistence.jooq.public_.Tables.EXECUTION_REPORT_SECTION;
import static io.aegisops.persistence.jooq.public_.Tables.EXECUTION_VERIFICATION;

import io.aegisops.execution.dto.ExecutionAuditEventCreateCommand;
import io.aegisops.execution.dto.ExecutionAuditEventRecord;
import io.aegisops.execution.dto.ExecutionReportCreateCommand;
import io.aegisops.execution.dto.ExecutionReportRecord;
import io.aegisops.execution.dto.ExecutionReportSectionCreateCommand;
import io.aegisops.execution.dto.ExecutionReportSectionRecord;
import io.aegisops.execution.dto.ExecutionVerificationCreateCommand;
import io.aegisops.execution.dto.ExecutionVerificationRecord;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqExecutionReportRepository implements ExecutionReportRepository {
  private final DSLContext dsl;

  public JooqExecutionReportRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void createReport(ExecutionReportCreateCommand command) {
    dsl.insertInto(EXECUTION_REPORT)
        .set(EXECUTION_REPORT.ID, command.id())
        .set(EXECUTION_REPORT.TENANT_ID, command.tenantId())
        .set(EXECUTION_REPORT.EXECUTION_ID, command.executionId())
        .set(EXECUTION_REPORT.REPORT_TYPE, command.reportType())
        .set(EXECUTION_REPORT.STATUS, command.status())
        .set(EXECUTION_REPORT.TITLE, command.title())
        .set(EXECUTION_REPORT.SUMMARY, command.summary())
        .set(EXECUTION_REPORT.MARKDOWN, command.markdown())
        .set(EXECUTION_REPORT.GENERATED_BY, command.generatedBy())
        .set(EXECUTION_REPORT.GENERATED_AT, DSL.currentOffsetDateTime())
        .set(EXECUTION_REPORT.CREATED_AT, DSL.currentOffsetDateTime())
        .set(EXECUTION_REPORT.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void createSection(ExecutionReportSectionCreateCommand command) {
    dsl.insertInto(EXECUTION_REPORT_SECTION)
        .set(EXECUTION_REPORT_SECTION.ID, command.id())
        .set(EXECUTION_REPORT_SECTION.TENANT_ID, command.tenantId())
        .set(EXECUTION_REPORT_SECTION.REPORT_ID, command.reportId())
        .set(EXECUTION_REPORT_SECTION.SECTION_ORDER, command.sectionOrder())
        .set(EXECUTION_REPORT_SECTION.SECTION_TYPE, command.sectionType())
        .set(EXECUTION_REPORT_SECTION.TITLE, command.title())
        .set(EXECUTION_REPORT_SECTION.CONTENT, command.content())
        .set(EXECUTION_REPORT_SECTION.METADATA, jsonbValue(command.metadataJson()))
        .set(EXECUTION_REPORT_SECTION.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public Optional<ExecutionReportRecord> findReport(String tenantId, String reportId) {
    return dsl.select(
            EXECUTION_REPORT.ID,
            EXECUTION_REPORT.TENANT_ID,
            EXECUTION_REPORT.EXECUTION_ID,
            EXECUTION_REPORT.REPORT_TYPE,
            EXECUTION_REPORT.STATUS,
            EXECUTION_REPORT.TITLE,
            EXECUTION_REPORT.SUMMARY,
            EXECUTION_REPORT.MARKDOWN,
            EXECUTION_REPORT.GENERATED_BY,
            EXECUTION_REPORT.GENERATED_AT,
            EXECUTION_REPORT.CREATED_AT,
            EXECUTION_REPORT.UPDATED_AT)
        .from(EXECUTION_REPORT)
        .where(EXECUTION_REPORT.TENANT_ID.eq(tenantId))
        .and(EXECUTION_REPORT.ID.eq(reportId))
        .fetchOptional(this::toReportRecord);
  }

  @Override
  public Optional<ExecutionReportRecord> findLatestReportByExecution(
      String tenantId, String executionId) {
    return dsl.select(
            EXECUTION_REPORT.ID,
            EXECUTION_REPORT.TENANT_ID,
            EXECUTION_REPORT.EXECUTION_ID,
            EXECUTION_REPORT.REPORT_TYPE,
            EXECUTION_REPORT.STATUS,
            EXECUTION_REPORT.TITLE,
            EXECUTION_REPORT.SUMMARY,
            EXECUTION_REPORT.MARKDOWN,
            EXECUTION_REPORT.GENERATED_BY,
            EXECUTION_REPORT.GENERATED_AT,
            EXECUTION_REPORT.CREATED_AT,
            EXECUTION_REPORT.UPDATED_AT)
        .from(EXECUTION_REPORT)
        .where(EXECUTION_REPORT.TENANT_ID.eq(tenantId))
        .and(EXECUTION_REPORT.EXECUTION_ID.eq(executionId))
        .orderBy(EXECUTION_REPORT.GENERATED_AT.desc())
        .limit(1)
        .fetchOptional(this::toReportRecord);
  }

  @Override
  public List<ExecutionReportSectionRecord> listSections(String tenantId, String reportId) {
    return dsl.select(
            EXECUTION_REPORT_SECTION.ID,
            EXECUTION_REPORT_SECTION.TENANT_ID,
            EXECUTION_REPORT_SECTION.REPORT_ID,
            EXECUTION_REPORT_SECTION.SECTION_ORDER,
            EXECUTION_REPORT_SECTION.SECTION_TYPE,
            EXECUTION_REPORT_SECTION.TITLE,
            EXECUTION_REPORT_SECTION.CONTENT,
            EXECUTION_REPORT_SECTION.METADATA.cast(String.class).as("metadata_json"),
            EXECUTION_REPORT_SECTION.CREATED_AT)
        .from(EXECUTION_REPORT_SECTION)
        .where(EXECUTION_REPORT_SECTION.TENANT_ID.eq(tenantId))
        .and(EXECUTION_REPORT_SECTION.REPORT_ID.eq(reportId))
        .orderBy(EXECUTION_REPORT_SECTION.SECTION_ORDER.asc())
        .fetch(this::toSectionRecord);
  }

  @Override
  public void createVerification(ExecutionVerificationCreateCommand command) {
    dsl.insertInto(EXECUTION_VERIFICATION)
        .set(EXECUTION_VERIFICATION.ID, command.id())
        .set(EXECUTION_VERIFICATION.TENANT_ID, command.tenantId())
        .set(EXECUTION_VERIFICATION.EXECUTION_ID, command.executionId())
        .set(EXECUTION_VERIFICATION.STEP_ID, command.stepId())
        .set(EXECUTION_VERIFICATION.VERIFICATION_TYPE, command.verificationType())
        .set(EXECUTION_VERIFICATION.TARGET_TYPE, command.targetType())
        .set(EXECUTION_VERIFICATION.TARGET_ID, command.targetId())
        .set(EXECUTION_VERIFICATION.STATUS, command.status())
        .set(EXECUTION_VERIFICATION.SUMMARY, command.summary())
        .set(EXECUTION_VERIFICATION.DETAILS, jsonbValue(command.detailsJson()))
        .set(EXECUTION_VERIFICATION.CREATED_BY, command.createdBy())
        .set(EXECUTION_VERIFICATION.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public List<ExecutionVerificationRecord> listVerifications(String tenantId, String executionId) {
    return dsl.select(
            EXECUTION_VERIFICATION.ID,
            EXECUTION_VERIFICATION.TENANT_ID,
            EXECUTION_VERIFICATION.EXECUTION_ID,
            EXECUTION_VERIFICATION.STEP_ID,
            EXECUTION_VERIFICATION.VERIFICATION_TYPE,
            EXECUTION_VERIFICATION.TARGET_TYPE,
            EXECUTION_VERIFICATION.TARGET_ID,
            EXECUTION_VERIFICATION.STATUS,
            EXECUTION_VERIFICATION.SUMMARY,
            EXECUTION_VERIFICATION.DETAILS.cast(String.class).as("details_json"),
            EXECUTION_VERIFICATION.CREATED_BY,
            EXECUTION_VERIFICATION.CREATED_AT)
        .from(EXECUTION_VERIFICATION)
        .where(EXECUTION_VERIFICATION.TENANT_ID.eq(tenantId))
        .and(EXECUTION_VERIFICATION.EXECUTION_ID.eq(executionId))
        .orderBy(EXECUTION_VERIFICATION.CREATED_AT.asc())
        .fetch(this::toVerificationRecord);
  }

  @Override
  public void createAuditEvent(ExecutionAuditEventCreateCommand command) {
    dsl.insertInto(EXECUTION_AUDIT_EVENT)
        .set(EXECUTION_AUDIT_EVENT.ID, command.id())
        .set(EXECUTION_AUDIT_EVENT.TENANT_ID, command.tenantId())
        .set(EXECUTION_AUDIT_EVENT.EXECUTION_ID, command.executionId())
        .set(EXECUTION_AUDIT_EVENT.STEP_ID, command.stepId())
        .set(EXECUTION_AUDIT_EVENT.EVENT_TYPE, command.eventType())
        .set(EXECUTION_AUDIT_EVENT.ACTOR, command.actor())
        .set(EXECUTION_AUDIT_EVENT.SUMMARY, command.summary())
        .set(EXECUTION_AUDIT_EVENT.PAYLOAD, jsonbValue(command.payloadJson()))
        .set(EXECUTION_AUDIT_EVENT.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public List<ExecutionAuditEventRecord> listAuditEvents(String tenantId, String executionId) {
    return dsl.select(
            EXECUTION_AUDIT_EVENT.ID,
            EXECUTION_AUDIT_EVENT.TENANT_ID,
            EXECUTION_AUDIT_EVENT.EXECUTION_ID,
            EXECUTION_AUDIT_EVENT.STEP_ID,
            EXECUTION_AUDIT_EVENT.EVENT_TYPE,
            EXECUTION_AUDIT_EVENT.ACTOR,
            EXECUTION_AUDIT_EVENT.SUMMARY,
            EXECUTION_AUDIT_EVENT.PAYLOAD.cast(String.class).as("payload_json"),
            EXECUTION_AUDIT_EVENT.CREATED_AT)
        .from(EXECUTION_AUDIT_EVENT)
        .where(EXECUTION_AUDIT_EVENT.TENANT_ID.eq(tenantId))
        .and(EXECUTION_AUDIT_EVENT.EXECUTION_ID.eq(executionId))
        .orderBy(EXECUTION_AUDIT_EVENT.CREATED_AT.asc())
        .fetch(this::toAuditEventRecord);
  }

  private ExecutionReportRecord toReportRecord(Record record) {
    return new ExecutionReportRecord(
        record.get(EXECUTION_REPORT.ID),
        record.get(EXECUTION_REPORT.TENANT_ID),
        record.get(EXECUTION_REPORT.EXECUTION_ID),
        record.get(EXECUTION_REPORT.REPORT_TYPE),
        record.get(EXECUTION_REPORT.STATUS),
        record.get(EXECUTION_REPORT.TITLE),
        record.get(EXECUTION_REPORT.SUMMARY),
        record.get(EXECUTION_REPORT.MARKDOWN),
        record.get(EXECUTION_REPORT.GENERATED_BY),
        record.get(EXECUTION_REPORT.GENERATED_AT),
        record.get(EXECUTION_REPORT.CREATED_AT),
        record.get(EXECUTION_REPORT.UPDATED_AT));
  }

  private ExecutionReportSectionRecord toSectionRecord(Record record) {
    return new ExecutionReportSectionRecord(
        record.get(EXECUTION_REPORT_SECTION.ID),
        record.get(EXECUTION_REPORT_SECTION.TENANT_ID),
        record.get(EXECUTION_REPORT_SECTION.REPORT_ID),
        value(record.get(EXECUTION_REPORT_SECTION.SECTION_ORDER)),
        record.get(EXECUTION_REPORT_SECTION.SECTION_TYPE),
        record.get(EXECUTION_REPORT_SECTION.TITLE),
        record.get(EXECUTION_REPORT_SECTION.CONTENT),
        record.get("metadata_json", String.class),
        record.get(EXECUTION_REPORT_SECTION.CREATED_AT));
  }

  private ExecutionVerificationRecord toVerificationRecord(Record record) {
    return new ExecutionVerificationRecord(
        record.get(EXECUTION_VERIFICATION.ID),
        record.get(EXECUTION_VERIFICATION.TENANT_ID),
        record.get(EXECUTION_VERIFICATION.EXECUTION_ID),
        record.get(EXECUTION_VERIFICATION.STEP_ID),
        record.get(EXECUTION_VERIFICATION.VERIFICATION_TYPE),
        record.get(EXECUTION_VERIFICATION.TARGET_TYPE),
        record.get(EXECUTION_VERIFICATION.TARGET_ID),
        record.get(EXECUTION_VERIFICATION.STATUS),
        record.get(EXECUTION_VERIFICATION.SUMMARY),
        record.get("details_json", String.class),
        record.get(EXECUTION_VERIFICATION.CREATED_BY),
        record.get(EXECUTION_VERIFICATION.CREATED_AT));
  }

  private ExecutionAuditEventRecord toAuditEventRecord(Record record) {
    return new ExecutionAuditEventRecord(
        record.get(EXECUTION_AUDIT_EVENT.ID),
        record.get(EXECUTION_AUDIT_EVENT.TENANT_ID),
        record.get(EXECUTION_AUDIT_EVENT.EXECUTION_ID),
        record.get(EXECUTION_AUDIT_EVENT.STEP_ID),
        record.get(EXECUTION_AUDIT_EVENT.EVENT_TYPE),
        record.get(EXECUTION_AUDIT_EVENT.ACTOR),
        record.get(EXECUTION_AUDIT_EVENT.SUMMARY),
        record.get("payload_json", String.class),
        record.get(EXECUTION_AUDIT_EVENT.CREATED_AT));
  }

  private int value(Integer value) {
    return value == null ? 0 : value;
  }
}
