package io.aegisops.ai.client;

import static io.aegisops.persistence.AegisJooq.jsonArrayOrEmpty;
import static io.aegisops.persistence.AegisJooq.jsonbArrayValue;
import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.Tables.AGENT_EVAL_RESULT;
import static io.aegisops.persistence.jooq.Tables.AGENT_RUN;
import static io.aegisops.persistence.jooq.Tables.AGENT_RUN_STEP;
import static io.aegisops.persistence.jooq.Tables.AI_DIAGNOSIS;
import static io.aegisops.persistence.jooq.Tables.ALERT_EVENT;
import static io.aegisops.persistence.jooq.Tables.INCIDENT;
import static io.aegisops.persistence.jooq.Tables.INCIDENT_EVENT;
import static io.aegisops.persistence.jooq.Tables.INCIDENT_TIMELINE;
import static io.aegisops.persistence.jooq.Tables.RCA_ANALYSIS;

import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import io.aegisops.ai.client.dto.AgentEvalResultCommand;
import io.aegisops.ai.client.dto.AgentEvalResultRecord;
import io.aegisops.ai.client.dto.AgentRunRecord;
import io.aegisops.ai.client.dto.AgentRunStepCommand;
import io.aegisops.ai.client.dto.AgentRunStepRecord;
import io.aegisops.ai.client.dto.AiAlertRecord;
import io.aegisops.ai.client.dto.AiDiagnosisRecord;
import io.aegisops.ai.client.dto.AiIncidentRecord;
import io.aegisops.ai.client.dto.AiRcaRecord;
import io.aegisops.ai.client.dto.SaveAgentRunCommand;
import io.aegisops.ai.client.dto.SaveDiagnosisCommand;
import io.aegisops.ai.client.dto.TimelineCommand;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/**
 * jOOQ generated Tables based repository for the AI client.
 *
 * <p>Phase4.7 将本类从 Phase4.5 的 no-codegen AegisTables 迁移到 {@code
 * io.aegisops.persistence.jooq.Tables}。
 */
@Repository
public class JdbcAiRepository implements AiRepository {
  private final DSLContext dsl;

  public JdbcAiRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public Optional<AiIncidentRecord> findIncident(String tenantId, String incidentId) {
    return dsl.select(
            INCIDENT.ID,
            INCIDENT.TENANT_ID,
            INCIDENT.TITLE,
            INCIDENT.SUMMARY,
            INCIDENT.SEVERITY,
            INCIDENT.STATUS,
            INCIDENT.SOURCE,
            INCIDENT.PRIMARY_ASSET_ID,
            INCIDENT.AGGREGATION_KEY,
            INCIDENT.ALERT_COUNT,
            INCIDENT.SUSPECTED_ROOT_CAUSE,
            INCIDENT.CONFIDENCE,
            INCIDENT.STARTED_AT,
            INCIDENT.DETECTED_AT,
            INCIDENT.LAST_SEEN_AT,
            INCIDENT.CREATED_AT,
            INCIDENT.UPDATED_AT)
        .from(INCIDENT)
        .where(incidentMatch(tenantId, incidentId))
        .fetchOptional(JdbcAiRepository::toAiIncidentRecord);
  }

  @Override
  public List<AiAlertRecord> listIncidentAlerts(String tenantId, String incidentId) {
    return dsl.select(
            ALERT_EVENT.ID,
            ALERT_EVENT.SOURCE,
            ALERT_EVENT.SOURCE_EVENT_ID,
            ALERT_EVENT.SEVERITY,
            ALERT_EVENT.TITLE,
            ALERT_EVENT.DESCRIPTION,
            ALERT_EVENT.ASSET_ID,
            ALERT_EVENT.ENTITY_TYPE,
            ALERT_EVENT.ENTITY_NAME,
            ALERT_EVENT.FINGERPRINT,
            ALERT_EVENT.LABELS.cast(String.class).as("labels_json"),
            ALERT_EVENT.STARTS_AT)
        .from(INCIDENT_EVENT)
        .join(INCIDENT)
        .on(INCIDENT.ID.eq(INCIDENT_EVENT.INCIDENT_ID))
        .join(ALERT_EVENT)
        .on(ALERT_EVENT.ID.eq(INCIDENT_EVENT.EVENT_ID))
        .where(incidentMatch(tenantId, incidentId))
        .and(INCIDENT_EVENT.EVENT_TYPE.eq("alert"))
        .and(ALERT_EVENT.TENANT_ID.eq(tenantId))
        .orderBy(ALERT_EVENT.STARTS_AT.asc())
        .fetch(JdbcAiRepository::toAiAlertRecord);
  }

  @Override
  public Optional<AiRcaRecord> findLatestRca(String tenantId, String incidentId) {
    return dsl.select(
            RCA_ANALYSIS.ID,
            RCA_ANALYSIS.SUSPECTED_ROOT_CAUSE,
            RCA_ANALYSIS.CONFIDENCE,
            RCA_ANALYSIS.SUMMARY,
            RCA_ANALYSIS.EVIDENCE.cast(String.class).as("evidence_json"),
            RCA_ANALYSIS.MODEL_VERSION,
            RCA_ANALYSIS.CREATED_AT)
        .from(RCA_ANALYSIS)
        .where(RCA_ANALYSIS.TENANT_ID.eq(tenantId))
        .and(RCA_ANALYSIS.INCIDENT_ID.eq(incidentId))
        .orderBy(RCA_ANALYSIS.CREATED_AT.desc())
        .limit(1)
        .fetchOptional(JdbcAiRepository::toAiRcaRecord);
  }

  @Override
  public Optional<AiDiagnosisRecord> findLatestDiagnosis(String tenantId, String incidentId) {
    return findDiagnosisByCondition(
        AI_DIAGNOSIS.TENANT_ID.eq(tenantId).and(AI_DIAGNOSIS.INCIDENT_ID.eq(incidentId)), true);
  }

  @Override
  public Optional<AiDiagnosisRecord> findDiagnosis(String tenantId, String diagnosisId) {
    return findDiagnosisByCondition(
        AI_DIAGNOSIS.TENANT_ID.eq(tenantId).and(AI_DIAGNOSIS.ID.eq(diagnosisId)), false);
  }

  @Override
  public void saveDiagnosis(SaveDiagnosisCommand command) {
    AgentDiagnosisResponse response = command.response();

    dsl.insertInto(AI_DIAGNOSIS)
        .set(AI_DIAGNOSIS.ID, command.id())
        .set(AI_DIAGNOSIS.TENANT_ID, command.tenantId())
        .set(AI_DIAGNOSIS.INCIDENT_ID, command.incidentId())
        .set(AI_DIAGNOSIS.STATUS, "completed")
        .set(AI_DIAGNOSIS.PROVIDER, response.provider())
        .set(AI_DIAGNOSIS.MODEL, response.model())
        .set(AI_DIAGNOSIS.AGENT_NAME, response.agentName())
        .set(AI_DIAGNOSIS.REQUEST_PAYLOAD, jsonbValue(command.requestJson()))
        .set(AI_DIAGNOSIS.RESPONSE_RAW, jsonbValue(command.rawJson()))
        .set(AI_DIAGNOSIS.SUMMARY, response.summary())
        .set(AI_DIAGNOSIS.ROOT_CAUSE, response.rootCause())
        .set(AI_DIAGNOSIS.IMPACT, response.impact())
        .set(AI_DIAGNOSIS.NEXT_STEPS, jsonbArrayValue(jsonArrayOrEmpty(command.nextStepsJson())))
        .set(
            AI_DIAGNOSIS.RUNBOOK_SUGGESTIONS,
            jsonbArrayValue(jsonArrayOrEmpty(command.runbookSuggestionsJson())))
        .set(AI_DIAGNOSIS.RISKS, jsonbArrayValue(jsonArrayOrEmpty(command.risksJson())))
        .set(AI_DIAGNOSIS.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void addIncidentTimeline(TimelineCommand command) {
    dsl.insertInto(INCIDENT_TIMELINE)
        .set(INCIDENT_TIMELINE.ID, command.id())
        .set(INCIDENT_TIMELINE.INCIDENT_ID, command.incidentId())
        .set(INCIDENT_TIMELINE.EVENT_TIME, command.eventTime())
        .set(INCIDENT_TIMELINE.EVENT_TYPE, "ai_diagnosed")
        .set(INCIDENT_TIMELINE.TITLE, command.title())
        .set(INCIDENT_TIMELINE.DESCRIPTION, command.description())
        .set(INCIDENT_TIMELINE.SOURCE, "system")
        .set(INCIDENT_TIMELINE.PAYLOAD, jsonbValue(command.payloadJson()))
        .execute();
  }

  @Override
  public void saveAgentRun(SaveAgentRunCommand command) {
    dsl.insertInto(AGENT_RUN)
        .set(AGENT_RUN.ID, command.id())
        .set(AGENT_RUN.DIAGNOSIS_ID, command.diagnosisId())
        .set(AGENT_RUN.TENANT_ID, command.tenantId())
        .set(AGENT_RUN.INCIDENT_ID, command.incidentId())
        .set(AGENT_RUN.TRACE_ID, command.traceId())
        .set(AGENT_RUN.CONTRACT_VERSION, command.contractVersion())
        .set(AGENT_RUN.GENERATION_MODE, command.generationMode())
        .set(AGENT_RUN.PROVIDER, command.provider())
        .set(AGENT_RUN.MODEL, command.model())
        .set(AGENT_RUN.STATUS, command.status())
        .set(AGENT_RUN.STARTED_AT, command.startedAt())
        .set(AGENT_RUN.FINISHED_AT, command.finishedAt())
        .set(AGENT_RUN.DURATION_MS, command.durationMs())
        .set(AGENT_RUN.FALLBACK_REASON, command.fallbackReason())
        .set(AGENT_RUN.SAFETY, jsonbValue(command.safetyJson()))
        .set(AGENT_RUN.EVAL_RESULT, jsonbValue(command.evalJson()))
        .set(AGENT_RUN.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void saveAgentRunSteps(List<AgentRunStepCommand> commands) {
    for (AgentRunStepCommand command : commands) {
      dsl.insertInto(AGENT_RUN_STEP)
          .set(AGENT_RUN_STEP.ID, command.id())
          .set(AGENT_RUN_STEP.RUN_ID, command.runId())
          .set(AGENT_RUN_STEP.SEQUENCE_NO, command.sequenceNo())
          .set(AGENT_RUN_STEP.STEP_NAME, command.stepName())
          .set(AGENT_RUN_STEP.STEP_TYPE, command.stepType())
          .set(AGENT_RUN_STEP.STATUS, command.status())
          .set(AGENT_RUN_STEP.STARTED_AT, command.startedAt())
          .set(AGENT_RUN_STEP.FINISHED_AT, command.finishedAt())
          .set(AGENT_RUN_STEP.DURATION_MS, command.durationMs())
          .set(AGENT_RUN_STEP.INPUT_SUMMARY, command.inputSummary())
          .set(AGENT_RUN_STEP.OUTPUT_SUMMARY, command.outputSummary())
          .set(AGENT_RUN_STEP.ERROR_MESSAGE, command.errorMessage())
          .set(AGENT_RUN_STEP.METADATA, jsonbValue(command.metadataJson()))
          .set(AGENT_RUN_STEP.CREATED_AT, DSL.currentOffsetDateTime())
          .execute();
    }
  }

  @Override
  public void saveAgentEvalResults(List<AgentEvalResultCommand> commands) {
    for (AgentEvalResultCommand command : commands) {
      dsl.insertInto(AGENT_EVAL_RESULT)
          .set(AGENT_EVAL_RESULT.ID, command.id())
          .set(AGENT_EVAL_RESULT.RUN_ID, command.runId())
          .set(AGENT_EVAL_RESULT.EVALUATOR_NAME, command.evaluatorName())
          .set(AGENT_EVAL_RESULT.CHECK_NAME, command.checkName())
          .set(AGENT_EVAL_RESULT.PASSED, command.passed())
          .set(AGENT_EVAL_RESULT.SCORE, command.score())
          .set(AGENT_EVAL_RESULT.REASON, command.reason())
          .set(AGENT_EVAL_RESULT.DETAILS, jsonbValue(command.detailsJson()))
          .set(AGENT_EVAL_RESULT.CREATED_AT, DSL.currentOffsetDateTime())
          .execute();
    }
  }

  @Override
  public Optional<AgentRunRecord> findLatestAgentRun(String tenantId, String incidentId) {
    return dsl.select(
            AGENT_RUN.ID,
            AGENT_RUN.DIAGNOSIS_ID,
            AGENT_RUN.INCIDENT_ID,
            AGENT_RUN.TRACE_ID,
            AGENT_RUN.CONTRACT_VERSION,
            AGENT_RUN.GENERATION_MODE,
            AGENT_RUN.PROVIDER,
            AGENT_RUN.MODEL,
            AGENT_RUN.STATUS,
            AGENT_RUN.STARTED_AT,
            AGENT_RUN.FINISHED_AT,
            AGENT_RUN.DURATION_MS,
            AGENT_RUN.FALLBACK_REASON,
            AGENT_RUN.SAFETY.cast(String.class).as("safety_json"),
            AGENT_RUN.EVAL_RESULT.cast(String.class).as("eval_json"),
            AGENT_RUN.CREATED_AT)
        .from(AGENT_RUN)
        .where(AGENT_RUN.TENANT_ID.eq(tenantId))
        .and(AGENT_RUN.INCIDENT_ID.eq(incidentId))
        .orderBy(AGENT_RUN.CREATED_AT.desc())
        .limit(1)
        .fetchOptional(JdbcAiRepository::toAgentRunRecord);
  }

  @Override
  public List<AgentRunStepRecord> listAgentRunSteps(String runId) {
    return dsl.select(
            AGENT_RUN_STEP.ID,
            AGENT_RUN_STEP.SEQUENCE_NO,
            AGENT_RUN_STEP.STEP_NAME,
            AGENT_RUN_STEP.STEP_TYPE,
            AGENT_RUN_STEP.STATUS,
            AGENT_RUN_STEP.STARTED_AT,
            AGENT_RUN_STEP.FINISHED_AT,
            AGENT_RUN_STEP.DURATION_MS,
            AGENT_RUN_STEP.INPUT_SUMMARY,
            AGENT_RUN_STEP.OUTPUT_SUMMARY,
            AGENT_RUN_STEP.ERROR_MESSAGE,
            AGENT_RUN_STEP.METADATA.cast(String.class).as("metadata_json"))
        .from(AGENT_RUN_STEP)
        .where(AGENT_RUN_STEP.RUN_ID.eq(runId))
        .orderBy(AGENT_RUN_STEP.SEQUENCE_NO.asc())
        .fetch(JdbcAiRepository::toAgentRunStepRecord);
  }

  @Override
  public List<AgentEvalResultRecord> listAgentEvalResults(String runId) {
    return dsl.select(
            AGENT_EVAL_RESULT.ID,
            AGENT_EVAL_RESULT.EVALUATOR_NAME,
            AGENT_EVAL_RESULT.CHECK_NAME,
            AGENT_EVAL_RESULT.PASSED,
            AGENT_EVAL_RESULT.SCORE,
            AGENT_EVAL_RESULT.REASON,
            AGENT_EVAL_RESULT.DETAILS.cast(String.class).as("details_json"),
            AGENT_EVAL_RESULT.CREATED_AT)
        .from(AGENT_EVAL_RESULT)
        .where(AGENT_EVAL_RESULT.RUN_ID.eq(runId))
        .orderBy(AGENT_EVAL_RESULT.CREATED_AT.asc())
        .fetch(JdbcAiRepository::toAgentEvalResultRecord);
  }

  private Optional<AiDiagnosisRecord> findDiagnosisByCondition(
      Condition condition, boolean latest) {
    var query =
        dsl.select(
                AI_DIAGNOSIS.ID,
                AI_DIAGNOSIS.TENANT_ID,
                AI_DIAGNOSIS.INCIDENT_ID,
                AI_DIAGNOSIS.STATUS,
                AI_DIAGNOSIS.PROVIDER,
                AI_DIAGNOSIS.MODEL,
                AI_DIAGNOSIS.AGENT_NAME,
                AI_DIAGNOSIS.SUMMARY,
                AI_DIAGNOSIS.ROOT_CAUSE,
                AI_DIAGNOSIS.IMPACT,
                AI_DIAGNOSIS.NEXT_STEPS.cast(String.class).as("next_steps_json"),
                AI_DIAGNOSIS.RUNBOOK_SUGGESTIONS.cast(String.class).as("runbook_suggestions_json"),
                AI_DIAGNOSIS.RISKS.cast(String.class).as("risks_json"),
                AI_DIAGNOSIS.CREATED_AT)
            .from(AI_DIAGNOSIS)
            .where(condition)
            .orderBy(latest ? AI_DIAGNOSIS.CREATED_AT.desc() : AI_DIAGNOSIS.ID.asc())
            .limit(1);

    return query.fetchOptional(JdbcAiRepository::toAiDiagnosisRecord);
  }

  private static Condition incidentMatch(String tenantId, String incidentId) {
    return INCIDENT.TENANT_ID.eq(tenantId).and(INCIDENT.ID.eq(incidentId));
  }

  private static AiIncidentRecord toAiIncidentRecord(org.jooq.Record record) {
    return new AiIncidentRecord(
        record.get(INCIDENT.ID),
        record.get(INCIDENT.TENANT_ID),
        record.get(INCIDENT.TITLE),
        record.get(INCIDENT.SUMMARY),
        record.get(INCIDENT.SEVERITY),
        record.get(INCIDENT.STATUS),
        record.get(INCIDENT.SOURCE),
        record.get(INCIDENT.PRIMARY_ASSET_ID),
        record.get(INCIDENT.AGGREGATION_KEY),
        value(record.get(INCIDENT.ALERT_COUNT)),
        record.get(INCIDENT.SUSPECTED_ROOT_CAUSE),
        record.get(INCIDENT.CONFIDENCE),
        record.get(INCIDENT.STARTED_AT),
        record.get(INCIDENT.DETECTED_AT),
        record.get(INCIDENT.LAST_SEEN_AT),
        record.get(INCIDENT.CREATED_AT),
        record.get(INCIDENT.UPDATED_AT));
  }

  private static AiAlertRecord toAiAlertRecord(org.jooq.Record record) {
    return new AiAlertRecord(
        record.get(ALERT_EVENT.ID),
        record.get(ALERT_EVENT.SOURCE),
        record.get(ALERT_EVENT.SOURCE_EVENT_ID),
        record.get(ALERT_EVENT.SEVERITY),
        record.get(ALERT_EVENT.TITLE),
        record.get(ALERT_EVENT.DESCRIPTION),
        record.get(ALERT_EVENT.ASSET_ID),
        record.get(ALERT_EVENT.ENTITY_TYPE),
        record.get(ALERT_EVENT.ENTITY_NAME),
        record.get(ALERT_EVENT.FINGERPRINT),
        record.get("labels_json", String.class),
        record.get(ALERT_EVENT.STARTS_AT));
  }

  private static AiRcaRecord toAiRcaRecord(org.jooq.Record record) {
    return new AiRcaRecord(
        record.get(RCA_ANALYSIS.ID),
        record.get(RCA_ANALYSIS.SUSPECTED_ROOT_CAUSE),
        record.get(RCA_ANALYSIS.CONFIDENCE),
        record.get(RCA_ANALYSIS.SUMMARY),
        record.get("evidence_json", String.class),
        record.get(RCA_ANALYSIS.MODEL_VERSION),
        record.get(RCA_ANALYSIS.CREATED_AT));
  }

  private static AiDiagnosisRecord toAiDiagnosisRecord(org.jooq.Record record) {
    return new AiDiagnosisRecord(
        record.get(AI_DIAGNOSIS.ID),
        record.get(AI_DIAGNOSIS.TENANT_ID),
        record.get(AI_DIAGNOSIS.INCIDENT_ID),
        record.get(AI_DIAGNOSIS.STATUS),
        record.get(AI_DIAGNOSIS.PROVIDER),
        record.get(AI_DIAGNOSIS.MODEL),
        record.get(AI_DIAGNOSIS.AGENT_NAME),
        record.get(AI_DIAGNOSIS.SUMMARY),
        record.get(AI_DIAGNOSIS.ROOT_CAUSE),
        record.get(AI_DIAGNOSIS.IMPACT),
        record.get("next_steps_json", String.class),
        record.get("runbook_suggestions_json", String.class),
        record.get("risks_json", String.class),
        record.get(AI_DIAGNOSIS.CREATED_AT));
  }

  private static AgentRunRecord toAgentRunRecord(org.jooq.Record record) {
    return new AgentRunRecord(
        record.get(AGENT_RUN.ID),
        record.get(AGENT_RUN.DIAGNOSIS_ID),
        record.get(AGENT_RUN.INCIDENT_ID),
        record.get(AGENT_RUN.TRACE_ID),
        record.get(AGENT_RUN.CONTRACT_VERSION),
        record.get(AGENT_RUN.GENERATION_MODE),
        record.get(AGENT_RUN.PROVIDER),
        record.get(AGENT_RUN.MODEL),
        record.get(AGENT_RUN.STATUS),
        record.get(AGENT_RUN.STARTED_AT),
        record.get(AGENT_RUN.FINISHED_AT),
        value(record.get(AGENT_RUN.DURATION_MS)),
        record.get(AGENT_RUN.FALLBACK_REASON),
        record.get("safety_json", String.class),
        record.get("eval_json", String.class),
        record.get(AGENT_RUN.CREATED_AT));
  }

  private static AgentRunStepRecord toAgentRunStepRecord(org.jooq.Record record) {
    return new AgentRunStepRecord(
        record.get(AGENT_RUN_STEP.ID),
        value(record.get(AGENT_RUN_STEP.SEQUENCE_NO)),
        record.get(AGENT_RUN_STEP.STEP_NAME),
        record.get(AGENT_RUN_STEP.STEP_TYPE),
        record.get(AGENT_RUN_STEP.STATUS),
        record.get(AGENT_RUN_STEP.STARTED_AT),
        record.get(AGENT_RUN_STEP.FINISHED_AT),
        value(record.get(AGENT_RUN_STEP.DURATION_MS)),
        record.get(AGENT_RUN_STEP.INPUT_SUMMARY),
        record.get(AGENT_RUN_STEP.OUTPUT_SUMMARY),
        record.get(AGENT_RUN_STEP.ERROR_MESSAGE),
        record.get("metadata_json", String.class));
  }

  private static AgentEvalResultRecord toAgentEvalResultRecord(org.jooq.Record record) {
    return new AgentEvalResultRecord(
        record.get(AGENT_EVAL_RESULT.ID),
        record.get(AGENT_EVAL_RESULT.EVALUATOR_NAME),
        record.get(AGENT_EVAL_RESULT.CHECK_NAME),
        Boolean.TRUE.equals(record.get(AGENT_EVAL_RESULT.PASSED)),
        record.get(AGENT_EVAL_RESULT.SCORE),
        record.get(AGENT_EVAL_RESULT.REASON),
        record.get("details_json", String.class),
        record.get(AGENT_EVAL_RESULT.CREATED_AT));
  }

  private static int value(Integer value) {
    return value == null ? 0 : value;
  }

  private static long value(Long value) {
    return value == null ? 0L : value;
  }
}
