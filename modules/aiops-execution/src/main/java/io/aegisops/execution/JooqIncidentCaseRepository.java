package io.aegisops.execution;

import static io.aegisops.persistence.jooq.Tables.INCIDENT_CASE;
import static io.aegisops.persistence.jooq.Tables.INCIDENT_CASE_RESOLUTION_STEP;
import static io.aegisops.persistence.jooq.Tables.INCIDENT_CASE_SYMPTOM;
import static io.aegisops.persistence.jooq.Tables.INCIDENT_CASE_TAG;

import io.aegisops.execution.dto.IncidentCaseCreateCommand;
import io.aegisops.execution.dto.IncidentCaseRecord;
import io.aegisops.execution.dto.IncidentCaseResolutionStepCreateCommand;
import io.aegisops.execution.dto.IncidentCaseResolutionStepRecord;
import io.aegisops.execution.dto.IncidentCaseSymptomCreateCommand;
import io.aegisops.execution.dto.IncidentCaseSymptomRecord;
import io.aegisops.execution.dto.IncidentCaseTagCreateCommand;
import io.aegisops.execution.dto.IncidentCaseTagRecord;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqIncidentCaseRepository implements IncidentCaseRepository {
  private final DSLContext dsl;

  public JooqIncidentCaseRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void createCase(IncidentCaseCreateCommand command) {
    dsl.insertInto(INCIDENT_CASE)
        .set(INCIDENT_CASE.ID, command.id())
        .set(INCIDENT_CASE.TENANT_ID, command.tenantId())
        .set(INCIDENT_CASE.SOURCE_POSTMORTEM_ID, command.sourcePostmortemId())
        .set(INCIDENT_CASE.INCIDENT_ID, command.incidentId())
        .set(INCIDENT_CASE.STATUS, command.status())
        .set(INCIDENT_CASE.SEVERITY, command.severity())
        .set(INCIDENT_CASE.TITLE, command.title())
        .set(INCIDENT_CASE.SUMMARY, command.summary())
        .set(INCIDENT_CASE.ROOT_CAUSE, command.rootCause())
        .set(INCIDENT_CASE.RESOLUTION, command.resolution())
        .set(INCIDENT_CASE.PREVENTION, command.prevention())
        .set(INCIDENT_CASE.QUALITY_SCORE, command.qualityScore())
        .set(INCIDENT_CASE.CREATED_BY, command.createdBy())
        .set(INCIDENT_CASE.CREATED_AT, DSL.currentOffsetDateTime())
        .set(INCIDENT_CASE.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void createSymptom(IncidentCaseSymptomCreateCommand command) {
    dsl.insertInto(INCIDENT_CASE_SYMPTOM)
        .set(INCIDENT_CASE_SYMPTOM.ID, command.id())
        .set(INCIDENT_CASE_SYMPTOM.TENANT_ID, command.tenantId())
        .set(INCIDENT_CASE_SYMPTOM.CASE_ID, command.caseId())
        .set(INCIDENT_CASE_SYMPTOM.SYMPTOM_TYPE, command.symptomType())
        .set(INCIDENT_CASE_SYMPTOM.NAME, command.name())
        .set(INCIDENT_CASE_SYMPTOM.DESCRIPTION, command.description())
        .set(INCIDENT_CASE_SYMPTOM.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void createResolutionStep(IncidentCaseResolutionStepCreateCommand command) {
    dsl.insertInto(INCIDENT_CASE_RESOLUTION_STEP)
        .set(INCIDENT_CASE_RESOLUTION_STEP.ID, command.id())
        .set(INCIDENT_CASE_RESOLUTION_STEP.TENANT_ID, command.tenantId())
        .set(INCIDENT_CASE_RESOLUTION_STEP.CASE_ID, command.caseId())
        .set(INCIDENT_CASE_RESOLUTION_STEP.STEP_ORDER, command.stepOrder())
        .set(INCIDENT_CASE_RESOLUTION_STEP.TITLE, command.title())
        .set(INCIDENT_CASE_RESOLUTION_STEP.DESCRIPTION, command.description())
        .set(INCIDENT_CASE_RESOLUTION_STEP.ACTION_TYPE, command.actionType())
        .set(INCIDENT_CASE_RESOLUTION_STEP.SOURCE_REF_ID, command.sourceRefId())
        .set(INCIDENT_CASE_RESOLUTION_STEP.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void createTag(IncidentCaseTagCreateCommand command) {
    dsl.insertInto(INCIDENT_CASE_TAG)
        .set(INCIDENT_CASE_TAG.ID, command.id())
        .set(INCIDENT_CASE_TAG.TENANT_ID, command.tenantId())
        .set(INCIDENT_CASE_TAG.CASE_ID, command.caseId())
        .set(INCIDENT_CASE_TAG.TAG, command.tag())
        .set(INCIDENT_CASE_TAG.CREATED_AT, DSL.currentOffsetDateTime())
        .onDuplicateKeyIgnore()
        .execute();
  }

  @Override
  public Optional<IncidentCaseRecord> findCase(String tenantId, String caseId) {
    return selectCase()
        .where(INCIDENT_CASE.TENANT_ID.eq(tenantId))
        .and(INCIDENT_CASE.ID.eq(caseId))
        .fetchOptional(this::toCaseRecord);
  }

  @Override
  public Optional<IncidentCaseRecord> findByPostmortem(String tenantId, String postmortemId) {
    return selectCase()
        .where(INCIDENT_CASE.TENANT_ID.eq(tenantId))
        .and(INCIDENT_CASE.SOURCE_POSTMORTEM_ID.eq(postmortemId))
        .fetchOptional(this::toCaseRecord);
  }

  @Override
  public Optional<IncidentCaseRecord> findLatestByIncident(String tenantId, String incidentId) {
    return selectCase()
        .where(INCIDENT_CASE.TENANT_ID.eq(tenantId))
        .and(INCIDENT_CASE.INCIDENT_ID.eq(incidentId))
        .orderBy(INCIDENT_CASE.CREATED_AT.desc())
        .limit(1)
        .fetchOptional(this::toCaseRecord);
  }

  @Override
  public List<IncidentCaseRecord> listCases(String tenantId, String status, String tag, int limit) {
    Condition condition = INCIDENT_CASE.TENANT_ID.eq(tenantId);

    if (status != null && !status.isBlank()) {
      condition = condition.and(INCIDENT_CASE.STATUS.eq(status));
    }

    if (tag != null && !tag.isBlank()) {
      return dsl.selectDistinct(
              INCIDENT_CASE.ID,
              INCIDENT_CASE.TENANT_ID,
              INCIDENT_CASE.SOURCE_POSTMORTEM_ID,
              INCIDENT_CASE.INCIDENT_ID,
              INCIDENT_CASE.STATUS,
              INCIDENT_CASE.SEVERITY,
              INCIDENT_CASE.TITLE,
              INCIDENT_CASE.SUMMARY,
              INCIDENT_CASE.ROOT_CAUSE,
              INCIDENT_CASE.RESOLUTION,
              INCIDENT_CASE.PREVENTION,
              INCIDENT_CASE.QUALITY_SCORE,
              INCIDENT_CASE.CREATED_BY,
              INCIDENT_CASE.REVIEWED_BY,
              INCIDENT_CASE.PUBLISHED_AT,
              INCIDENT_CASE.ARCHIVED_AT,
              INCIDENT_CASE.CREATED_AT,
              INCIDENT_CASE.UPDATED_AT)
          .from(
              INCIDENT_CASE
                  .join(INCIDENT_CASE_TAG)
                  .on(INCIDENT_CASE_TAG.CASE_ID.eq(INCIDENT_CASE.ID))
                  .and(INCIDENT_CASE_TAG.TENANT_ID.eq(INCIDENT_CASE.TENANT_ID)))
          .where(condition.and(INCIDENT_CASE_TAG.TAG.eq(tag)))
          .orderBy(INCIDENT_CASE.QUALITY_SCORE.desc(), INCIDENT_CASE.CREATED_AT.desc())
          .limit(Math.max(1, Math.min(limit, 100)))
          .fetch(this::toCaseRecord);
    }

    return dsl.selectDistinct(
            INCIDENT_CASE.ID,
            INCIDENT_CASE.TENANT_ID,
            INCIDENT_CASE.SOURCE_POSTMORTEM_ID,
            INCIDENT_CASE.INCIDENT_ID,
            INCIDENT_CASE.STATUS,
            INCIDENT_CASE.SEVERITY,
            INCIDENT_CASE.TITLE,
            INCIDENT_CASE.SUMMARY,
            INCIDENT_CASE.ROOT_CAUSE,
            INCIDENT_CASE.RESOLUTION,
            INCIDENT_CASE.PREVENTION,
            INCIDENT_CASE.QUALITY_SCORE,
            INCIDENT_CASE.CREATED_BY,
            INCIDENT_CASE.REVIEWED_BY,
            INCIDENT_CASE.PUBLISHED_AT,
            INCIDENT_CASE.ARCHIVED_AT,
            INCIDENT_CASE.CREATED_AT,
            INCIDENT_CASE.UPDATED_AT)
        .from(INCIDENT_CASE)
        .where(condition)
        .orderBy(INCIDENT_CASE.QUALITY_SCORE.desc(), INCIDENT_CASE.CREATED_AT.desc())
        .limit(Math.max(1, Math.min(limit, 100)))
        .fetch(this::toCaseRecord);
  }

  @Override
  public List<IncidentCaseSymptomRecord> listSymptoms(String tenantId, String caseId) {
    return dsl.select(
            INCIDENT_CASE_SYMPTOM.ID,
            INCIDENT_CASE_SYMPTOM.TENANT_ID,
            INCIDENT_CASE_SYMPTOM.CASE_ID,
            INCIDENT_CASE_SYMPTOM.SYMPTOM_TYPE,
            INCIDENT_CASE_SYMPTOM.NAME,
            INCIDENT_CASE_SYMPTOM.DESCRIPTION,
            INCIDENT_CASE_SYMPTOM.CREATED_AT)
        .from(INCIDENT_CASE_SYMPTOM)
        .where(INCIDENT_CASE_SYMPTOM.TENANT_ID.eq(tenantId))
        .and(INCIDENT_CASE_SYMPTOM.CASE_ID.eq(caseId))
        .orderBy(INCIDENT_CASE_SYMPTOM.CREATED_AT.asc())
        .fetch(this::toSymptomRecord);
  }

  @Override
  public List<IncidentCaseResolutionStepRecord> listResolutionSteps(
      String tenantId, String caseId) {
    return dsl.select(
            INCIDENT_CASE_RESOLUTION_STEP.ID,
            INCIDENT_CASE_RESOLUTION_STEP.TENANT_ID,
            INCIDENT_CASE_RESOLUTION_STEP.CASE_ID,
            INCIDENT_CASE_RESOLUTION_STEP.STEP_ORDER,
            INCIDENT_CASE_RESOLUTION_STEP.TITLE,
            INCIDENT_CASE_RESOLUTION_STEP.DESCRIPTION,
            INCIDENT_CASE_RESOLUTION_STEP.ACTION_TYPE,
            INCIDENT_CASE_RESOLUTION_STEP.SOURCE_REF_ID,
            INCIDENT_CASE_RESOLUTION_STEP.CREATED_AT)
        .from(INCIDENT_CASE_RESOLUTION_STEP)
        .where(INCIDENT_CASE_RESOLUTION_STEP.TENANT_ID.eq(tenantId))
        .and(INCIDENT_CASE_RESOLUTION_STEP.CASE_ID.eq(caseId))
        .orderBy(INCIDENT_CASE_RESOLUTION_STEP.STEP_ORDER.asc())
        .fetch(this::toResolutionStepRecord);
  }

  @Override
  public List<IncidentCaseTagRecord> listTags(String tenantId, String caseId) {
    return dsl.select(
            INCIDENT_CASE_TAG.ID,
            INCIDENT_CASE_TAG.TENANT_ID,
            INCIDENT_CASE_TAG.CASE_ID,
            INCIDENT_CASE_TAG.TAG,
            INCIDENT_CASE_TAG.CREATED_AT)
        .from(INCIDENT_CASE_TAG)
        .where(INCIDENT_CASE_TAG.TENANT_ID.eq(tenantId))
        .and(INCIDENT_CASE_TAG.CASE_ID.eq(caseId))
        .orderBy(INCIDENT_CASE_TAG.TAG.asc())
        .fetch(this::toTagRecord);
  }

  @Override
  public boolean publish(String tenantId, String caseId, String reviewer) {
    return dsl.update(INCIDENT_CASE)
            .set(INCIDENT_CASE.STATUS, "published")
            .set(INCIDENT_CASE.REVIEWED_BY, reviewer)
            .set(INCIDENT_CASE.PUBLISHED_AT, DSL.currentOffsetDateTime())
            .set(INCIDENT_CASE.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(INCIDENT_CASE.TENANT_ID.eq(tenantId))
            .and(INCIDENT_CASE.ID.eq(caseId))
            .and(INCIDENT_CASE.STATUS.eq("draft"))
            .execute()
        > 0;
  }

  @Override
  public boolean archive(String tenantId, String caseId) {
    return dsl.update(INCIDENT_CASE)
            .set(INCIDENT_CASE.STATUS, "archived")
            .set(INCIDENT_CASE.ARCHIVED_AT, DSL.currentOffsetDateTime())
            .set(INCIDENT_CASE.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(INCIDENT_CASE.TENANT_ID.eq(tenantId))
            .and(INCIDENT_CASE.ID.eq(caseId))
            .and(INCIDENT_CASE.STATUS.ne("archived"))
            .execute()
        > 0;
  }

  private org.jooq.SelectJoinStep<?> selectCase() {
    return dsl.select(
            INCIDENT_CASE.ID,
            INCIDENT_CASE.TENANT_ID,
            INCIDENT_CASE.SOURCE_POSTMORTEM_ID,
            INCIDENT_CASE.INCIDENT_ID,
            INCIDENT_CASE.STATUS,
            INCIDENT_CASE.SEVERITY,
            INCIDENT_CASE.TITLE,
            INCIDENT_CASE.SUMMARY,
            INCIDENT_CASE.ROOT_CAUSE,
            INCIDENT_CASE.RESOLUTION,
            INCIDENT_CASE.PREVENTION,
            INCIDENT_CASE.QUALITY_SCORE,
            INCIDENT_CASE.CREATED_BY,
            INCIDENT_CASE.REVIEWED_BY,
            INCIDENT_CASE.PUBLISHED_AT,
            INCIDENT_CASE.ARCHIVED_AT,
            INCIDENT_CASE.CREATED_AT,
            INCIDENT_CASE.UPDATED_AT)
        .from(INCIDENT_CASE);
  }

  private IncidentCaseRecord toCaseRecord(org.jooq.Record record) {
    return new IncidentCaseRecord(
        record.get(INCIDENT_CASE.ID),
        record.get(INCIDENT_CASE.TENANT_ID),
        record.get(INCIDENT_CASE.SOURCE_POSTMORTEM_ID),
        record.get(INCIDENT_CASE.INCIDENT_ID),
        record.get(INCIDENT_CASE.STATUS),
        record.get(INCIDENT_CASE.SEVERITY),
        record.get(INCIDENT_CASE.TITLE),
        record.get(INCIDENT_CASE.SUMMARY),
        record.get(INCIDENT_CASE.ROOT_CAUSE),
        record.get(INCIDENT_CASE.RESOLUTION),
        record.get(INCIDENT_CASE.PREVENTION),
        value(record.get(INCIDENT_CASE.QUALITY_SCORE)),
        record.get(INCIDENT_CASE.CREATED_BY),
        record.get(INCIDENT_CASE.REVIEWED_BY),
        record.get(INCIDENT_CASE.PUBLISHED_AT),
        record.get(INCIDENT_CASE.ARCHIVED_AT),
        record.get(INCIDENT_CASE.CREATED_AT),
        record.get(INCIDENT_CASE.UPDATED_AT));
  }

  private IncidentCaseSymptomRecord toSymptomRecord(org.jooq.Record record) {
    return new IncidentCaseSymptomRecord(
        record.get(INCIDENT_CASE_SYMPTOM.ID),
        record.get(INCIDENT_CASE_SYMPTOM.TENANT_ID),
        record.get(INCIDENT_CASE_SYMPTOM.CASE_ID),
        record.get(INCIDENT_CASE_SYMPTOM.SYMPTOM_TYPE),
        record.get(INCIDENT_CASE_SYMPTOM.NAME),
        record.get(INCIDENT_CASE_SYMPTOM.DESCRIPTION),
        record.get(INCIDENT_CASE_SYMPTOM.CREATED_AT));
  }

  private IncidentCaseResolutionStepRecord toResolutionStepRecord(org.jooq.Record record) {
    return new IncidentCaseResolutionStepRecord(
        record.get(INCIDENT_CASE_RESOLUTION_STEP.ID),
        record.get(INCIDENT_CASE_RESOLUTION_STEP.TENANT_ID),
        record.get(INCIDENT_CASE_RESOLUTION_STEP.CASE_ID),
        value(record.get(INCIDENT_CASE_RESOLUTION_STEP.STEP_ORDER)),
        record.get(INCIDENT_CASE_RESOLUTION_STEP.TITLE),
        record.get(INCIDENT_CASE_RESOLUTION_STEP.DESCRIPTION),
        record.get(INCIDENT_CASE_RESOLUTION_STEP.ACTION_TYPE),
        record.get(INCIDENT_CASE_RESOLUTION_STEP.SOURCE_REF_ID),
        record.get(INCIDENT_CASE_RESOLUTION_STEP.CREATED_AT));
  }

  private IncidentCaseTagRecord toTagRecord(org.jooq.Record record) {
    return new IncidentCaseTagRecord(
        record.get(INCIDENT_CASE_TAG.ID),
        record.get(INCIDENT_CASE_TAG.TENANT_ID),
        record.get(INCIDENT_CASE_TAG.CASE_ID),
        record.get(INCIDENT_CASE_TAG.TAG),
        record.get(INCIDENT_CASE_TAG.CREATED_AT));
  }

  private int value(Integer value) {
    return value == null ? 0 : value;
  }
}
