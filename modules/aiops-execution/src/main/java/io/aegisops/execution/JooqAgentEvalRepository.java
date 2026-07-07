package io.aegisops.execution;

import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.public_.Tables.AGENT_EVAL_CASE;
import static io.aegisops.persistence.jooq.public_.Tables.AGENT_EVAL_CASE_RESULT;
import static io.aegisops.persistence.jooq.public_.Tables.AGENT_EVAL_DATASET;
import static io.aegisops.persistence.jooq.public_.Tables.AGENT_EVAL_RUN;
import static io.aegisops.persistence.jooq.public_.Tables.AGENT_PROMPT_PROFILE;

import io.aegisops.execution.dto.AgentEvalCaseCreateCommand;
import io.aegisops.execution.dto.AgentEvalCaseRecord;
import io.aegisops.execution.dto.AgentEvalCaseResultCreateCommand;
import io.aegisops.execution.dto.AgentEvalCaseResultRecord;
import io.aegisops.execution.dto.AgentEvalDatasetCreateCommand;
import io.aegisops.execution.dto.AgentEvalDatasetRecord;
import io.aegisops.execution.dto.AgentEvalRunCreateCommand;
import io.aegisops.execution.dto.AgentEvalRunFinishCommand;
import io.aegisops.execution.dto.AgentEvalRunRecord;
import io.aegisops.execution.dto.AgentPromptProfileCreateCommand;
import io.aegisops.execution.dto.AgentPromptProfileRecord;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqAgentEvalRepository implements AgentEvalRepository {
  private final DSLContext dsl;

  public JooqAgentEvalRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void createDataset(AgentEvalDatasetCreateCommand command) {
    dsl.insertInto(AGENT_EVAL_DATASET)
        .set(AGENT_EVAL_DATASET.ID, command.id())
        .set(AGENT_EVAL_DATASET.TENANT_ID, command.tenantId())
        .set(AGENT_EVAL_DATASET.NAME, command.name())
        .set(AGENT_EVAL_DATASET.DESCRIPTION, command.description())
        .set(AGENT_EVAL_DATASET.STATUS, command.status())
        .set(AGENT_EVAL_DATASET.CREATED_BY, command.createdBy())
        .set(AGENT_EVAL_DATASET.CREATED_AT, DSL.currentOffsetDateTime())
        .set(AGENT_EVAL_DATASET.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public Optional<AgentEvalDatasetRecord> findDataset(String tenantId, String datasetId) {
    return selectDataset()
        .where(AGENT_EVAL_DATASET.TENANT_ID.eq(tenantId))
        .and(AGENT_EVAL_DATASET.ID.eq(datasetId))
        .fetchOptional(this::toDatasetRecord);
  }

  @Override
  public List<AgentEvalDatasetRecord> listDatasets(String tenantId, String status) {
    Condition condition = AGENT_EVAL_DATASET.TENANT_ID.eq(tenantId);

    if (status != null && !status.isBlank()) {
      condition = condition.and(AGENT_EVAL_DATASET.STATUS.eq(status));
    }

    return selectDataset()
        .where(condition)
        .orderBy(AGENT_EVAL_DATASET.CREATED_AT.desc())
        .fetch(this::toDatasetRecord);
  }

  @Override
  public boolean updateDatasetStatus(
      String tenantId, String datasetId, String fromStatus, String toStatus) {
    return dsl.update(AGENT_EVAL_DATASET)
            .set(AGENT_EVAL_DATASET.STATUS, toStatus)
            .set(AGENT_EVAL_DATASET.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(AGENT_EVAL_DATASET.TENANT_ID.eq(tenantId))
            .and(AGENT_EVAL_DATASET.ID.eq(datasetId))
            .and(AGENT_EVAL_DATASET.STATUS.eq(fromStatus))
            .execute()
        > 0;
  }

  @Override
  public void createCase(AgentEvalCaseCreateCommand command) {
    dsl.insertInto(AGENT_EVAL_CASE)
        .set(AGENT_EVAL_CASE.ID, command.id())
        .set(AGENT_EVAL_CASE.TENANT_ID, command.tenantId())
        .set(AGENT_EVAL_CASE.DATASET_ID, command.datasetId())
        .set(AGENT_EVAL_CASE.SOURCE_TYPE, command.sourceType())
        .set(AGENT_EVAL_CASE.SOURCE_ID, command.sourceId())
        .set(AGENT_EVAL_CASE.INCIDENT_ID, command.incidentId())
        .set(AGENT_EVAL_CASE.TITLE, command.title())
        .set(AGENT_EVAL_CASE.SEVERITY, command.severity())
        .set(AGENT_EVAL_CASE.INPUT_CONTEXT, command.inputContext())
        .set(AGENT_EVAL_CASE.EXPECTED_ROOT_CAUSE, command.expectedRootCause())
        .set(AGENT_EVAL_CASE.EXPECTED_KEYWORDS, jsonbValue(command.expectedKeywordsJson()))
        .set(AGENT_EVAL_CASE.EXPECTED_ACTIONS, jsonbValue(command.expectedActionsJson()))
        .set(AGENT_EVAL_CASE.FORBIDDEN_ACTIONS, jsonbValue(command.forbiddenActionsJson()))
        .set(AGENT_EVAL_CASE.TAGS, jsonbValue(command.tagsJson()))
        .set(AGENT_EVAL_CASE.ENABLED, command.enabled())
        .set(AGENT_EVAL_CASE.CREATED_BY, command.createdBy())
        .set(AGENT_EVAL_CASE.CREATED_AT, DSL.currentOffsetDateTime())
        .set(AGENT_EVAL_CASE.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public List<AgentEvalCaseRecord> listCases(
      String tenantId, String datasetId, boolean onlyEnabled) {
    Condition condition =
        AGENT_EVAL_CASE.TENANT_ID.eq(tenantId).and(AGENT_EVAL_CASE.DATASET_ID.eq(datasetId));

    if (onlyEnabled) {
      condition = condition.and(AGENT_EVAL_CASE.ENABLED.eq(true));
    }

    return selectCase()
        .where(condition)
        .orderBy(AGENT_EVAL_CASE.CREATED_AT.asc())
        .fetch(this::toCaseRecord);
  }

  @Override
  public Optional<AgentEvalCaseRecord> findCase(String tenantId, String caseId) {
    return selectCase()
        .where(AGENT_EVAL_CASE.TENANT_ID.eq(tenantId))
        .and(AGENT_EVAL_CASE.ID.eq(caseId))
        .fetchOptional(this::toCaseRecord);
  }

  @Override
  public void createPromptProfile(AgentPromptProfileCreateCommand command) {
    dsl.insertInto(AGENT_PROMPT_PROFILE)
        .set(AGENT_PROMPT_PROFILE.ID, command.id())
        .set(AGENT_PROMPT_PROFILE.TENANT_ID, command.tenantId())
        .set(AGENT_PROMPT_PROFILE.NAME, command.name())
        .set(AGENT_PROMPT_PROFILE.VERSION, command.version())
        .set(AGENT_PROMPT_PROFILE.STATUS, command.status())
        .set(AGENT_PROMPT_PROFILE.SYSTEM_PROMPT, command.systemPrompt())
        .set(AGENT_PROMPT_PROFILE.DIAGNOSIS_PROMPT_TEMPLATE, command.diagnosisPromptTemplate())
        .set(AGENT_PROMPT_PROFILE.METADATA, jsonbValue(command.metadataJson()))
        .set(AGENT_PROMPT_PROFILE.CREATED_BY, command.createdBy())
        .set(AGENT_PROMPT_PROFILE.CREATED_AT, DSL.currentOffsetDateTime())
        .set(AGENT_PROMPT_PROFILE.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public Optional<AgentPromptProfileRecord> findPromptProfile(String tenantId, String profileId) {
    return selectPromptProfile()
        .where(AGENT_PROMPT_PROFILE.TENANT_ID.eq(tenantId))
        .and(AGENT_PROMPT_PROFILE.ID.eq(profileId))
        .fetchOptional(this::toPromptProfileRecord);
  }

  @Override
  public List<AgentPromptProfileRecord> listPromptProfiles(String tenantId, String status) {
    Condition condition = AGENT_PROMPT_PROFILE.TENANT_ID.eq(tenantId);
    if (status != null && !status.isBlank()) {
      condition = condition.and(AGENT_PROMPT_PROFILE.STATUS.eq(status));
    }

    return selectPromptProfile()
        .where(condition)
        .orderBy(AGENT_PROMPT_PROFILE.CREATED_AT.desc())
        .fetch(this::toPromptProfileRecord);
  }

  @Override
  public boolean archivePromptProfile(String tenantId, String profileId) {
    return dsl.update(AGENT_PROMPT_PROFILE)
            .set(AGENT_PROMPT_PROFILE.STATUS, "archived")
            .set(AGENT_PROMPT_PROFILE.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(AGENT_PROMPT_PROFILE.TENANT_ID.eq(tenantId))
            .and(AGENT_PROMPT_PROFILE.ID.eq(profileId))
            .and(AGENT_PROMPT_PROFILE.STATUS.eq("active"))
            .execute()
        > 0;
  }

  @Override
  public void createRun(AgentEvalRunCreateCommand command) {
    dsl.insertInto(AGENT_EVAL_RUN)
        .set(AGENT_EVAL_RUN.ID, command.id())
        .set(AGENT_EVAL_RUN.TENANT_ID, command.tenantId())
        .set(AGENT_EVAL_RUN.DATASET_ID, command.datasetId())
        .set(AGENT_EVAL_RUN.PROMPT_PROFILE_ID, command.promptProfileId())
        .set(AGENT_EVAL_RUN.MODE, command.mode())
        .set(AGENT_EVAL_RUN.STATUS, command.status())
        .set(AGENT_EVAL_RUN.TOTAL_CASES, command.totalCases())
        .set(AGENT_EVAL_RUN.PASSED_CASES, command.passedCases())
        .set(AGENT_EVAL_RUN.FAILED_CASES, command.failedCases())
        .set(AGENT_EVAL_RUN.AVERAGE_SCORE, BigDecimal.valueOf(command.averageScore()))
        .set(AGENT_EVAL_RUN.SUMMARY, command.summary())
        .set(AGENT_EVAL_RUN.CREATED_BY, command.createdBy())
        .set(AGENT_EVAL_RUN.STARTED_AT, DSL.currentOffsetDateTime())
        .set(AGENT_EVAL_RUN.CREATED_AT, DSL.currentOffsetDateTime())
        .set(AGENT_EVAL_RUN.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public Optional<AgentEvalRunRecord> findRun(String tenantId, String runId) {
    return selectRun()
        .where(AGENT_EVAL_RUN.TENANT_ID.eq(tenantId))
        .and(AGENT_EVAL_RUN.ID.eq(runId))
        .fetchOptional(this::toRunRecord);
  }

  @Override
  public boolean finishRun(AgentEvalRunFinishCommand command) {
    return dsl.update(AGENT_EVAL_RUN)
            .set(AGENT_EVAL_RUN.STATUS, command.status())
            .set(AGENT_EVAL_RUN.TOTAL_CASES, command.totalCases())
            .set(AGENT_EVAL_RUN.PASSED_CASES, command.passedCases())
            .set(AGENT_EVAL_RUN.FAILED_CASES, command.failedCases())
            .set(AGENT_EVAL_RUN.AVERAGE_SCORE, BigDecimal.valueOf(command.averageScore()))
            .set(AGENT_EVAL_RUN.SUMMARY, command.summary())
            .set(AGENT_EVAL_RUN.FINISHED_AT, DSL.currentOffsetDateTime())
            .set(AGENT_EVAL_RUN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(AGENT_EVAL_RUN.TENANT_ID.eq(command.tenantId()))
            .and(AGENT_EVAL_RUN.ID.eq(command.runId()))
            .and(AGENT_EVAL_RUN.STATUS.eq("running"))
            .execute()
        > 0;
  }

  @Override
  public void createCaseResult(AgentEvalCaseResultCreateCommand command) {
    dsl.insertInto(AGENT_EVAL_CASE_RESULT)
        .set(AGENT_EVAL_CASE_RESULT.ID, command.id())
        .set(AGENT_EVAL_CASE_RESULT.TENANT_ID, command.tenantId())
        .set(AGENT_EVAL_CASE_RESULT.RUN_ID, command.runId())
        .set(AGENT_EVAL_CASE_RESULT.CASE_ID, command.caseId())
        .set(AGENT_EVAL_CASE_RESULT.ACTUAL_DIAGNOSIS_ID, command.actualDiagnosisId())
        .set(AGENT_EVAL_CASE_RESULT.ACTUAL_SUMMARY, command.actualSummary())
        .set(AGENT_EVAL_CASE_RESULT.ACTUAL_ROOT_CAUSE, command.actualRootCause())
        .set(AGENT_EVAL_CASE_RESULT.ACTUAL_RECOMMENDATION, command.actualRecommendation())
        .set(AGENT_EVAL_CASE_RESULT.SCORE, BigDecimal.valueOf(command.score()))
        .set(AGENT_EVAL_CASE_RESULT.ROOT_CAUSE_SCORE, BigDecimal.valueOf(command.rootCauseScore()))
        .set(AGENT_EVAL_CASE_RESULT.KEYWORD_SCORE, BigDecimal.valueOf(command.keywordScore()))
        .set(AGENT_EVAL_CASE_RESULT.ACTION_SCORE, BigDecimal.valueOf(command.actionScore()))
        .set(AGENT_EVAL_CASE_RESULT.SAFETY_SCORE, BigDecimal.valueOf(command.safetyScore()))
        .set(AGENT_EVAL_CASE_RESULT.PASSED, command.passed())
        .set(AGENT_EVAL_CASE_RESULT.DETAILS, jsonbValue(command.detailsJson()))
        .set(AGENT_EVAL_CASE_RESULT.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public List<AgentEvalCaseResultRecord> listCaseResults(String tenantId, String runId) {
    return selectCaseResult()
        .where(AGENT_EVAL_CASE_RESULT.TENANT_ID.eq(tenantId))
        .and(AGENT_EVAL_CASE_RESULT.RUN_ID.eq(runId))
        .orderBy(AGENT_EVAL_CASE_RESULT.CREATED_AT.asc())
        .fetch(this::toCaseResultRecord);
  }

  private org.jooq.SelectJoinStep<?> selectDataset() {
    return dsl.select(
            AGENT_EVAL_DATASET.ID,
            AGENT_EVAL_DATASET.TENANT_ID,
            AGENT_EVAL_DATASET.NAME,
            AGENT_EVAL_DATASET.DESCRIPTION,
            AGENT_EVAL_DATASET.STATUS,
            AGENT_EVAL_DATASET.CREATED_BY,
            AGENT_EVAL_DATASET.CREATED_AT,
            AGENT_EVAL_DATASET.UPDATED_AT)
        .from(AGENT_EVAL_DATASET);
  }

  private org.jooq.SelectJoinStep<?> selectCase() {
    return dsl.select(
            AGENT_EVAL_CASE.ID,
            AGENT_EVAL_CASE.TENANT_ID,
            AGENT_EVAL_CASE.DATASET_ID,
            AGENT_EVAL_CASE.SOURCE_TYPE,
            AGENT_EVAL_CASE.SOURCE_ID,
            AGENT_EVAL_CASE.INCIDENT_ID,
            AGENT_EVAL_CASE.TITLE,
            AGENT_EVAL_CASE.SEVERITY,
            AGENT_EVAL_CASE.INPUT_CONTEXT,
            AGENT_EVAL_CASE.EXPECTED_ROOT_CAUSE,
            AGENT_EVAL_CASE.EXPECTED_KEYWORDS.cast(String.class).as("expected_keywords_json"),
            AGENT_EVAL_CASE.EXPECTED_ACTIONS.cast(String.class).as("expected_actions_json"),
            AGENT_EVAL_CASE.FORBIDDEN_ACTIONS.cast(String.class).as("forbidden_actions_json"),
            AGENT_EVAL_CASE.TAGS.cast(String.class).as("tags_json"),
            AGENT_EVAL_CASE.ENABLED,
            AGENT_EVAL_CASE.CREATED_BY,
            AGENT_EVAL_CASE.CREATED_AT,
            AGENT_EVAL_CASE.UPDATED_AT)
        .from(AGENT_EVAL_CASE);
  }

  private org.jooq.SelectJoinStep<?> selectPromptProfile() {
    return dsl.select(
            AGENT_PROMPT_PROFILE.ID,
            AGENT_PROMPT_PROFILE.TENANT_ID,
            AGENT_PROMPT_PROFILE.NAME,
            AGENT_PROMPT_PROFILE.VERSION,
            AGENT_PROMPT_PROFILE.STATUS,
            AGENT_PROMPT_PROFILE.SYSTEM_PROMPT,
            AGENT_PROMPT_PROFILE.DIAGNOSIS_PROMPT_TEMPLATE,
            AGENT_PROMPT_PROFILE.METADATA.cast(String.class).as("metadata_json"),
            AGENT_PROMPT_PROFILE.CREATED_BY,
            AGENT_PROMPT_PROFILE.CREATED_AT,
            AGENT_PROMPT_PROFILE.UPDATED_AT)
        .from(AGENT_PROMPT_PROFILE);
  }

  private org.jooq.SelectJoinStep<?> selectRun() {
    return dsl.select(
            AGENT_EVAL_RUN.ID,
            AGENT_EVAL_RUN.TENANT_ID,
            AGENT_EVAL_RUN.DATASET_ID,
            AGENT_EVAL_RUN.PROMPT_PROFILE_ID,
            AGENT_EVAL_RUN.MODE,
            AGENT_EVAL_RUN.STATUS,
            AGENT_EVAL_RUN.TOTAL_CASES,
            AGENT_EVAL_RUN.PASSED_CASES,
            AGENT_EVAL_RUN.FAILED_CASES,
            AGENT_EVAL_RUN.AVERAGE_SCORE,
            AGENT_EVAL_RUN.SUMMARY,
            AGENT_EVAL_RUN.CREATED_BY,
            AGENT_EVAL_RUN.STARTED_AT,
            AGENT_EVAL_RUN.FINISHED_AT,
            AGENT_EVAL_RUN.CREATED_AT,
            AGENT_EVAL_RUN.UPDATED_AT)
        .from(AGENT_EVAL_RUN);
  }

  private org.jooq.SelectJoinStep<?> selectCaseResult() {
    return dsl.select(
            AGENT_EVAL_CASE_RESULT.ID,
            AGENT_EVAL_CASE_RESULT.TENANT_ID,
            AGENT_EVAL_CASE_RESULT.RUN_ID,
            AGENT_EVAL_CASE_RESULT.CASE_ID,
            AGENT_EVAL_CASE_RESULT.ACTUAL_DIAGNOSIS_ID,
            AGENT_EVAL_CASE_RESULT.ACTUAL_SUMMARY,
            AGENT_EVAL_CASE_RESULT.ACTUAL_ROOT_CAUSE,
            AGENT_EVAL_CASE_RESULT.ACTUAL_RECOMMENDATION,
            AGENT_EVAL_CASE_RESULT.SCORE,
            AGENT_EVAL_CASE_RESULT.ROOT_CAUSE_SCORE,
            AGENT_EVAL_CASE_RESULT.KEYWORD_SCORE,
            AGENT_EVAL_CASE_RESULT.ACTION_SCORE,
            AGENT_EVAL_CASE_RESULT.SAFETY_SCORE,
            AGENT_EVAL_CASE_RESULT.PASSED,
            AGENT_EVAL_CASE_RESULT.DETAILS.cast(String.class).as("details_json"),
            AGENT_EVAL_CASE_RESULT.CREATED_AT)
        .from(AGENT_EVAL_CASE_RESULT);
  }

  private AgentEvalDatasetRecord toDatasetRecord(org.jooq.Record record) {
    return new AgentEvalDatasetRecord(
        record.get(AGENT_EVAL_DATASET.ID),
        record.get(AGENT_EVAL_DATASET.TENANT_ID),
        record.get(AGENT_EVAL_DATASET.NAME),
        record.get(AGENT_EVAL_DATASET.DESCRIPTION),
        record.get(AGENT_EVAL_DATASET.STATUS),
        record.get(AGENT_EVAL_DATASET.CREATED_BY),
        record.get(AGENT_EVAL_DATASET.CREATED_AT),
        record.get(AGENT_EVAL_DATASET.UPDATED_AT));
  }

  private AgentEvalCaseRecord toCaseRecord(org.jooq.Record record) {
    return new AgentEvalCaseRecord(
        record.get(AGENT_EVAL_CASE.ID),
        record.get(AGENT_EVAL_CASE.TENANT_ID),
        record.get(AGENT_EVAL_CASE.DATASET_ID),
        record.get(AGENT_EVAL_CASE.SOURCE_TYPE),
        record.get(AGENT_EVAL_CASE.SOURCE_ID),
        record.get(AGENT_EVAL_CASE.INCIDENT_ID),
        record.get(AGENT_EVAL_CASE.TITLE),
        record.get(AGENT_EVAL_CASE.SEVERITY),
        record.get(AGENT_EVAL_CASE.INPUT_CONTEXT),
        record.get(AGENT_EVAL_CASE.EXPECTED_ROOT_CAUSE),
        record.get("expected_keywords_json", String.class),
        record.get("expected_actions_json", String.class),
        record.get("forbidden_actions_json", String.class),
        record.get("tags_json", String.class),
        Boolean.TRUE.equals(record.get(AGENT_EVAL_CASE.ENABLED)),
        record.get(AGENT_EVAL_CASE.CREATED_BY),
        record.get(AGENT_EVAL_CASE.CREATED_AT),
        record.get(AGENT_EVAL_CASE.UPDATED_AT));
  }

  private AgentPromptProfileRecord toPromptProfileRecord(org.jooq.Record record) {
    return new AgentPromptProfileRecord(
        record.get(AGENT_PROMPT_PROFILE.ID),
        record.get(AGENT_PROMPT_PROFILE.TENANT_ID),
        record.get(AGENT_PROMPT_PROFILE.NAME),
        record.get(AGENT_PROMPT_PROFILE.VERSION),
        record.get(AGENT_PROMPT_PROFILE.STATUS),
        record.get(AGENT_PROMPT_PROFILE.SYSTEM_PROMPT),
        record.get(AGENT_PROMPT_PROFILE.DIAGNOSIS_PROMPT_TEMPLATE),
        record.get("metadata_json", String.class),
        record.get(AGENT_PROMPT_PROFILE.CREATED_BY),
        record.get(AGENT_PROMPT_PROFILE.CREATED_AT),
        record.get(AGENT_PROMPT_PROFILE.UPDATED_AT));
  }

  private AgentEvalRunRecord toRunRecord(org.jooq.Record record) {
    return new AgentEvalRunRecord(
        record.get(AGENT_EVAL_RUN.ID),
        record.get(AGENT_EVAL_RUN.TENANT_ID),
        record.get(AGENT_EVAL_RUN.DATASET_ID),
        record.get(AGENT_EVAL_RUN.PROMPT_PROFILE_ID),
        record.get(AGENT_EVAL_RUN.MODE),
        record.get(AGENT_EVAL_RUN.STATUS),
        value(record.get(AGENT_EVAL_RUN.TOTAL_CASES)),
        value(record.get(AGENT_EVAL_RUN.PASSED_CASES)),
        value(record.get(AGENT_EVAL_RUN.FAILED_CASES)),
        doubleValue(record.get(AGENT_EVAL_RUN.AVERAGE_SCORE)),
        record.get(AGENT_EVAL_RUN.SUMMARY),
        record.get(AGENT_EVAL_RUN.CREATED_BY),
        record.get(AGENT_EVAL_RUN.STARTED_AT),
        record.get(AGENT_EVAL_RUN.FINISHED_AT),
        record.get(AGENT_EVAL_RUN.CREATED_AT),
        record.get(AGENT_EVAL_RUN.UPDATED_AT));
  }

  private AgentEvalCaseResultRecord toCaseResultRecord(org.jooq.Record record) {
    return new AgentEvalCaseResultRecord(
        record.get(AGENT_EVAL_CASE_RESULT.ID),
        record.get(AGENT_EVAL_CASE_RESULT.TENANT_ID),
        record.get(AGENT_EVAL_CASE_RESULT.RUN_ID),
        record.get(AGENT_EVAL_CASE_RESULT.CASE_ID),
        record.get(AGENT_EVAL_CASE_RESULT.ACTUAL_DIAGNOSIS_ID),
        record.get(AGENT_EVAL_CASE_RESULT.ACTUAL_SUMMARY),
        record.get(AGENT_EVAL_CASE_RESULT.ACTUAL_ROOT_CAUSE),
        record.get(AGENT_EVAL_CASE_RESULT.ACTUAL_RECOMMENDATION),
        doubleValue(record.get(AGENT_EVAL_CASE_RESULT.SCORE)),
        doubleValue(record.get(AGENT_EVAL_CASE_RESULT.ROOT_CAUSE_SCORE)),
        doubleValue(record.get(AGENT_EVAL_CASE_RESULT.KEYWORD_SCORE)),
        doubleValue(record.get(AGENT_EVAL_CASE_RESULT.ACTION_SCORE)),
        doubleValue(record.get(AGENT_EVAL_CASE_RESULT.SAFETY_SCORE)),
        Boolean.TRUE.equals(record.get(AGENT_EVAL_CASE_RESULT.PASSED)),
        record.get("details_json", String.class),
        record.get(AGENT_EVAL_CASE_RESULT.CREATED_AT));
  }

  private int value(Integer value) {
    return value == null ? 0 : value;
  }

  private double doubleValue(BigDecimal value) {
    return value == null ? 0.0d : value.doubleValue();
  }
}
