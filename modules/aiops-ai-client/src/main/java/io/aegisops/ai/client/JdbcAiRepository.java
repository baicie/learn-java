package io.aegisops.ai.client;

import static io.aegisops.persistence.AegisJooq.jsonArrayOrEmpty;
import static io.aegisops.persistence.AegisJooq.jsonbArrayValue;
import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.AegisTables.AGENT_EVAL_RESULT;
import static io.aegisops.persistence.AegisTables.AGENT_RUN;
import static io.aegisops.persistence.AegisTables.AGENT_RUN_STEP;
import static io.aegisops.persistence.AegisTables.AI_DIAGNOSIS;
import static io.aegisops.persistence.AegisTables.ALERT_EVENT;
import static io.aegisops.persistence.AegisTables.INCIDENT;
import static io.aegisops.persistence.AegisTables.INCIDENT_EVENT;
import static io.aegisops.persistence.AegisTables.INCIDENT_TIMELINE;
import static io.aegisops.persistence.AegisTables.RCA_ANALYSIS;
import static io.aegisops.persistence.AegisTables.bool;
import static io.aegisops.persistence.AegisTables.decimal;
import static io.aegisops.persistence.AegisTables.integer;
import static io.aegisops.persistence.AegisTables.jsonb;
import static io.aegisops.persistence.AegisTables.lng;
import static io.aegisops.persistence.AegisTables.str;
import static io.aegisops.persistence.AegisTables.time;

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
 * jOOQ based repository for the AI client. 内部使用 {@link DSLContext} 替代原来的 {@code JdbcTemplate}，但
 * {@link AiRepository} interface 不变。
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
            str(INCIDENT, "id"),
            str(INCIDENT, "tenant_id"),
            str(INCIDENT, "title"),
            str(INCIDENT, "summary"),
            str(INCIDENT, "severity"),
            str(INCIDENT, "status"),
            str(INCIDENT, "source"),
            str(INCIDENT, "primary_asset_id"),
            str(INCIDENT, "aggregation_key"),
            integer(INCIDENT, "alert_count"),
            str(INCIDENT, "suspected_root_cause"),
            decimal(INCIDENT, "confidence"),
            time(INCIDENT, "started_at"),
            time(INCIDENT, "detected_at"),
            time(INCIDENT, "last_seen_at"),
            time(INCIDENT, "created_at"),
            time(INCIDENT, "updated_at"))
        .from(INCIDENT)
        .where(incidentMatch(tenantId, incidentId))
        .fetchOptional(JdbcAiRepository::toAiIncidentRecord);
  }

  @Override
  public List<AiAlertRecord> listIncidentAlerts(String tenantId, String incidentId) {
    return dsl.select(
            str(ALERT_EVENT, "id"),
            str(ALERT_EVENT, "source"),
            str(ALERT_EVENT, "source_event_id"),
            str(ALERT_EVENT, "severity"),
            str(ALERT_EVENT, "title"),
            str(ALERT_EVENT, "description"),
            str(ALERT_EVENT, "asset_id"),
            str(ALERT_EVENT, "entity_type"),
            str(ALERT_EVENT, "entity_name"),
            str(ALERT_EVENT, "fingerprint"),
            jsonb(ALERT_EVENT, "labels").cast(String.class).as("labels_json"),
            time(ALERT_EVENT, "starts_at"))
        .from(INCIDENT_EVENT)
        .join(INCIDENT)
        .on(str(INCIDENT, "id").eq(str(INCIDENT_EVENT, "incident_id")))
        .join(ALERT_EVENT)
        .on(str(ALERT_EVENT, "id").eq(str(INCIDENT_EVENT, "event_id")))
        .where(incidentMatch(tenantId, incidentId))
        .and(str(INCIDENT_EVENT, "event_type").eq("alert"))
        .and(str(ALERT_EVENT, "tenant_id").eq(tenantId))
        .orderBy(time(ALERT_EVENT, "starts_at").asc())
        .fetch(JdbcAiRepository::toAiAlertRecord);
  }

  @Override
  public Optional<AiRcaRecord> findLatestRca(String tenantId, String incidentId) {
    return dsl.select(
            str(RCA_ANALYSIS, "id"),
            str(RCA_ANALYSIS, "suspected_root_cause"),
            decimal(RCA_ANALYSIS, "confidence"),
            str(RCA_ANALYSIS, "summary"),
            jsonb(RCA_ANALYSIS, "evidence").cast(String.class).as("evidence_json"),
            str(RCA_ANALYSIS, "model_version"),
            time(RCA_ANALYSIS, "created_at"))
        .from(RCA_ANALYSIS)
        .where(str(RCA_ANALYSIS, "tenant_id").eq(tenantId))
        .and(str(RCA_ANALYSIS, "incident_id").eq(incidentId))
        .orderBy(time(RCA_ANALYSIS, "created_at").desc())
        .limit(1)
        .fetchOptional(JdbcAiRepository::toAiRcaRecord);
  }

  @Override
  public Optional<AiDiagnosisRecord> findLatestDiagnosis(String tenantId, String incidentId) {
    return findDiagnosisByCondition(
        str(AI_DIAGNOSIS, "tenant_id")
            .eq(tenantId)
            .and(str(AI_DIAGNOSIS, "incident_id").eq(incidentId)),
        true);
  }

  @Override
  public Optional<AiDiagnosisRecord> findDiagnosis(String tenantId, String diagnosisId) {
    return findDiagnosisByCondition(
        str(AI_DIAGNOSIS, "tenant_id").eq(tenantId).and(str(AI_DIAGNOSIS, "id").eq(diagnosisId)),
        false);
  }

  @Override
  public void saveDiagnosis(SaveDiagnosisCommand command) {
    AgentDiagnosisResponse response = command.response();

    dsl.insertInto(AI_DIAGNOSIS)
        .set(str(AI_DIAGNOSIS, "id"), command.id())
        .set(str(AI_DIAGNOSIS, "tenant_id"), command.tenantId())
        .set(str(AI_DIAGNOSIS, "incident_id"), command.incidentId())
        .set(str(AI_DIAGNOSIS, "status"), "completed")
        .set(str(AI_DIAGNOSIS, "provider"), response.provider())
        .set(str(AI_DIAGNOSIS, "model"), response.model())
        .set(str(AI_DIAGNOSIS, "agent_name"), response.agentName())
        .set(jsonb(AI_DIAGNOSIS, "request_payload"), jsonbValue(command.requestJson()))
        .set(jsonb(AI_DIAGNOSIS, "response_raw"), jsonbValue(command.rawJson()))
        .set(str(AI_DIAGNOSIS, "summary"), response.summary())
        .set(str(AI_DIAGNOSIS, "root_cause"), response.rootCause())
        .set(str(AI_DIAGNOSIS, "impact"), response.impact())
        .set(
            jsonb(AI_DIAGNOSIS, "next_steps"),
            jsonbArrayValue(jsonArrayOrEmpty(command.nextStepsJson())))
        .set(
            jsonb(AI_DIAGNOSIS, "runbook_suggestions"),
            jsonbArrayValue(jsonArrayOrEmpty(command.runbookSuggestionsJson())))
        .set(jsonb(AI_DIAGNOSIS, "risks"), jsonbArrayValue(jsonArrayOrEmpty(command.risksJson())))
        .set(time(AI_DIAGNOSIS, "created_at"), DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void addIncidentTimeline(TimelineCommand command) {
    dsl.insertInto(INCIDENT_TIMELINE)
        .set(str(INCIDENT_TIMELINE, "id"), command.id())
        .set(str(INCIDENT_TIMELINE, "incident_id"), command.incidentId())
        .set(time(INCIDENT_TIMELINE, "event_time"), command.eventTime())
        .set(str(INCIDENT_TIMELINE, "event_type"), "ai_diagnosed")
        .set(str(INCIDENT_TIMELINE, "title"), command.title())
        .set(str(INCIDENT_TIMELINE, "description"), command.description())
        .set(str(INCIDENT_TIMELINE, "source"), "system")
        .set(jsonb(INCIDENT_TIMELINE, "payload"), jsonbValue(command.payloadJson()))
        .execute();
  }

  @Override
  public void saveAgentRun(SaveAgentRunCommand command) {
    dsl.insertInto(AGENT_RUN)
        .set(str(AGENT_RUN, "id"), command.id())
        .set(str(AGENT_RUN, "diagnosis_id"), command.diagnosisId())
        .set(str(AGENT_RUN, "tenant_id"), command.tenantId())
        .set(str(AGENT_RUN, "incident_id"), command.incidentId())
        .set(str(AGENT_RUN, "trace_id"), command.traceId())
        .set(str(AGENT_RUN, "contract_version"), command.contractVersion())
        .set(str(AGENT_RUN, "generation_mode"), command.generationMode())
        .set(str(AGENT_RUN, "provider"), command.provider())
        .set(str(AGENT_RUN, "model"), command.model())
        .set(str(AGENT_RUN, "status"), command.status())
        .set(time(AGENT_RUN, "started_at"), command.startedAt())
        .set(time(AGENT_RUN, "finished_at"), command.finishedAt())
        .set(lng(AGENT_RUN, "duration_ms"), command.durationMs())
        .set(str(AGENT_RUN, "fallback_reason"), command.fallbackReason())
        .set(jsonb(AGENT_RUN, "safety"), jsonbValue(command.safetyJson()))
        .set(jsonb(AGENT_RUN, "eval_result"), jsonbValue(command.evalJson()))
        .set(time(AGENT_RUN, "created_at"), DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void saveAgentRunSteps(List<AgentRunStepCommand> commands) {
    for (AgentRunStepCommand command : commands) {
      dsl.insertInto(AGENT_RUN_STEP)
          .set(str(AGENT_RUN_STEP, "id"), command.id())
          .set(str(AGENT_RUN_STEP, "run_id"), command.runId())
          .set(integer(AGENT_RUN_STEP, "sequence_no"), command.sequenceNo())
          .set(str(AGENT_RUN_STEP, "step_name"), command.stepName())
          .set(str(AGENT_RUN_STEP, "step_type"), command.stepType())
          .set(str(AGENT_RUN_STEP, "status"), command.status())
          .set(time(AGENT_RUN_STEP, "started_at"), command.startedAt())
          .set(time(AGENT_RUN_STEP, "finished_at"), command.finishedAt())
          .set(lng(AGENT_RUN_STEP, "duration_ms"), command.durationMs())
          .set(str(AGENT_RUN_STEP, "input_summary"), command.inputSummary())
          .set(str(AGENT_RUN_STEP, "output_summary"), command.outputSummary())
          .set(str(AGENT_RUN_STEP, "error_message"), command.errorMessage())
          .set(jsonb(AGENT_RUN_STEP, "metadata"), jsonbValue(command.metadataJson()))
          .set(time(AGENT_RUN_STEP, "created_at"), DSL.currentOffsetDateTime())
          .execute();
    }
  }

  @Override
  public void saveAgentEvalResults(List<AgentEvalResultCommand> commands) {
    for (AgentEvalResultCommand command : commands) {
      dsl.insertInto(AGENT_EVAL_RESULT)
          .set(str(AGENT_EVAL_RESULT, "id"), command.id())
          .set(str(AGENT_EVAL_RESULT, "run_id"), command.runId())
          .set(str(AGENT_EVAL_RESULT, "evaluator_name"), command.evaluatorName())
          .set(str(AGENT_EVAL_RESULT, "check_name"), command.checkName())
          .set(bool(AGENT_EVAL_RESULT, "passed"), command.passed())
          .set(decimal(AGENT_EVAL_RESULT, "score"), command.score())
          .set(str(AGENT_EVAL_RESULT, "reason"), command.reason())
          .set(jsonb(AGENT_EVAL_RESULT, "details"), jsonbValue(command.detailsJson()))
          .set(time(AGENT_EVAL_RESULT, "created_at"), DSL.currentOffsetDateTime())
          .execute();
    }
  }

  @Override
  public Optional<AgentRunRecord> findLatestAgentRun(String tenantId, String incidentId) {
    return dsl.select(
            str(AGENT_RUN, "id"),
            str(AGENT_RUN, "diagnosis_id"),
            str(AGENT_RUN, "incident_id"),
            str(AGENT_RUN, "trace_id"),
            str(AGENT_RUN, "contract_version"),
            str(AGENT_RUN, "generation_mode"),
            str(AGENT_RUN, "provider"),
            str(AGENT_RUN, "model"),
            str(AGENT_RUN, "status"),
            time(AGENT_RUN, "started_at"),
            time(AGENT_RUN, "finished_at"),
            lng(AGENT_RUN, "duration_ms"),
            str(AGENT_RUN, "fallback_reason"),
            jsonb(AGENT_RUN, "safety").cast(String.class).as("safety_json"),
            jsonb(AGENT_RUN, "eval_result").cast(String.class).as("eval_json"),
            time(AGENT_RUN, "created_at"))
        .from(AGENT_RUN)
        .where(str(AGENT_RUN, "tenant_id").eq(tenantId))
        .and(str(AGENT_RUN, "incident_id").eq(incidentId))
        .orderBy(time(AGENT_RUN, "created_at").desc())
        .limit(1)
        .fetchOptional(JdbcAiRepository::toAgentRunRecord);
  }

  @Override
  public List<AgentRunStepRecord> listAgentRunSteps(String runId) {
    return dsl.select(
            str(AGENT_RUN_STEP, "id"),
            integer(AGENT_RUN_STEP, "sequence_no"),
            str(AGENT_RUN_STEP, "step_name"),
            str(AGENT_RUN_STEP, "step_type"),
            str(AGENT_RUN_STEP, "status"),
            time(AGENT_RUN_STEP, "started_at"),
            time(AGENT_RUN_STEP, "finished_at"),
            lng(AGENT_RUN_STEP, "duration_ms"),
            str(AGENT_RUN_STEP, "input_summary"),
            str(AGENT_RUN_STEP, "output_summary"),
            str(AGENT_RUN_STEP, "error_message"),
            jsonb(AGENT_RUN_STEP, "metadata").cast(String.class).as("metadata_json"))
        .from(AGENT_RUN_STEP)
        .where(str(AGENT_RUN_STEP, "run_id").eq(runId))
        .orderBy(integer(AGENT_RUN_STEP, "sequence_no").asc())
        .fetch(JdbcAiRepository::toAgentRunStepRecord);
  }

  @Override
  public List<AgentEvalResultRecord> listAgentEvalResults(String runId) {
    return dsl.select(
            str(AGENT_EVAL_RESULT, "id"),
            str(AGENT_EVAL_RESULT, "evaluator_name"),
            str(AGENT_EVAL_RESULT, "check_name"),
            bool(AGENT_EVAL_RESULT, "passed"),
            decimal(AGENT_EVAL_RESULT, "score"),
            str(AGENT_EVAL_RESULT, "reason"),
            jsonb(AGENT_EVAL_RESULT, "details").cast(String.class).as("details_json"),
            time(AGENT_EVAL_RESULT, "created_at"))
        .from(AGENT_EVAL_RESULT)
        .where(str(AGENT_EVAL_RESULT, "run_id").eq(runId))
        .orderBy(time(AGENT_EVAL_RESULT, "created_at").asc())
        .fetch(JdbcAiRepository::toAgentEvalResultRecord);
  }

  private Optional<AiDiagnosisRecord> findDiagnosisByCondition(
      Condition condition, boolean latest) {
    var query =
        dsl.select(
                str(AI_DIAGNOSIS, "id"),
                str(AI_DIAGNOSIS, "tenant_id"),
                str(AI_DIAGNOSIS, "incident_id"),
                str(AI_DIAGNOSIS, "status"),
                str(AI_DIAGNOSIS, "provider"),
                str(AI_DIAGNOSIS, "model"),
                str(AI_DIAGNOSIS, "agent_name"),
                str(AI_DIAGNOSIS, "summary"),
                str(AI_DIAGNOSIS, "root_cause"),
                str(AI_DIAGNOSIS, "impact"),
                jsonb(AI_DIAGNOSIS, "next_steps").cast(String.class).as("next_steps_json"),
                jsonb(AI_DIAGNOSIS, "runbook_suggestions")
                    .cast(String.class)
                    .as("runbook_suggestions_json"),
                jsonb(AI_DIAGNOSIS, "risks").cast(String.class).as("risks_json"),
                time(AI_DIAGNOSIS, "created_at"))
            .from(AI_DIAGNOSIS)
            .where(condition)
            .orderBy(
                latest ? time(AI_DIAGNOSIS, "created_at").desc() : str(AI_DIAGNOSIS, "id").asc())
            .limit(1);

    return query.fetchOptional(JdbcAiRepository::toAiDiagnosisRecord);
  }

  private static Condition incidentMatch(String tenantId, String incidentId) {
    return str(INCIDENT, "tenant_id").eq(tenantId).and(str(INCIDENT, "id").eq(incidentId));
  }

  private static AiIncidentRecord toAiIncidentRecord(org.jooq.Record record) {
    return new AiIncidentRecord(
        record.get(str(INCIDENT, "id")),
        record.get(str(INCIDENT, "tenant_id")),
        record.get(str(INCIDENT, "title")),
        record.get(str(INCIDENT, "summary")),
        record.get(str(INCIDENT, "severity")),
        record.get(str(INCIDENT, "status")),
        record.get(str(INCIDENT, "source")),
        record.get(str(INCIDENT, "primary_asset_id")),
        record.get(str(INCIDENT, "aggregation_key")),
        value(record.get(integer(INCIDENT, "alert_count"))),
        record.get(str(INCIDENT, "suspected_root_cause")),
        record.get(decimal(INCIDENT, "confidence")),
        record.get(time(INCIDENT, "started_at")),
        record.get(time(INCIDENT, "detected_at")),
        record.get(time(INCIDENT, "last_seen_at")),
        record.get(time(INCIDENT, "created_at")),
        record.get(time(INCIDENT, "updated_at")));
  }

  private static AiAlertRecord toAiAlertRecord(org.jooq.Record record) {
    return new AiAlertRecord(
        record.get(str(ALERT_EVENT, "id")),
        record.get(str(ALERT_EVENT, "source")),
        record.get(str(ALERT_EVENT, "source_event_id")),
        record.get(str(ALERT_EVENT, "severity")),
        record.get(str(ALERT_EVENT, "title")),
        record.get(str(ALERT_EVENT, "description")),
        record.get(str(ALERT_EVENT, "asset_id")),
        record.get(str(ALERT_EVENT, "entity_type")),
        record.get(str(ALERT_EVENT, "entity_name")),
        record.get(str(ALERT_EVENT, "fingerprint")),
        record.get("labels_json", String.class),
        record.get(time(ALERT_EVENT, "starts_at")));
  }

  private static AiRcaRecord toAiRcaRecord(org.jooq.Record record) {
    return new AiRcaRecord(
        record.get(str(RCA_ANALYSIS, "id")),
        record.get(str(RCA_ANALYSIS, "suspected_root_cause")),
        record.get(decimal(RCA_ANALYSIS, "confidence")),
        record.get(str(RCA_ANALYSIS, "summary")),
        record.get("evidence_json", String.class),
        record.get(str(RCA_ANALYSIS, "model_version")),
        record.get(time(RCA_ANALYSIS, "created_at")));
  }

  private static AiDiagnosisRecord toAiDiagnosisRecord(org.jooq.Record record) {
    return new AiDiagnosisRecord(
        record.get(str(AI_DIAGNOSIS, "id")),
        record.get(str(AI_DIAGNOSIS, "tenant_id")),
        record.get(str(AI_DIAGNOSIS, "incident_id")),
        record.get(str(AI_DIAGNOSIS, "status")),
        record.get(str(AI_DIAGNOSIS, "provider")),
        record.get(str(AI_DIAGNOSIS, "model")),
        record.get(str(AI_DIAGNOSIS, "agent_name")),
        record.get(str(AI_DIAGNOSIS, "summary")),
        record.get(str(AI_DIAGNOSIS, "root_cause")),
        record.get(str(AI_DIAGNOSIS, "impact")),
        record.get("next_steps_json", String.class),
        record.get("runbook_suggestions_json", String.class),
        record.get("risks_json", String.class),
        record.get(time(AI_DIAGNOSIS, "created_at")));
  }

  private static AgentRunRecord toAgentRunRecord(org.jooq.Record record) {
    return new AgentRunRecord(
        record.get(str(AGENT_RUN, "id")),
        record.get(str(AGENT_RUN, "diagnosis_id")),
        record.get(str(AGENT_RUN, "incident_id")),
        record.get(str(AGENT_RUN, "trace_id")),
        record.get(str(AGENT_RUN, "contract_version")),
        record.get(str(AGENT_RUN, "generation_mode")),
        record.get(str(AGENT_RUN, "provider")),
        record.get(str(AGENT_RUN, "model")),
        record.get(str(AGENT_RUN, "status")),
        record.get(time(AGENT_RUN, "started_at")),
        record.get(time(AGENT_RUN, "finished_at")),
        value(record.get(lng(AGENT_RUN, "duration_ms"))),
        record.get(str(AGENT_RUN, "fallback_reason")),
        record.get("safety_json", String.class),
        record.get("eval_json", String.class),
        record.get(time(AGENT_RUN, "created_at")));
  }

  private static AgentRunStepRecord toAgentRunStepRecord(org.jooq.Record record) {
    return new AgentRunStepRecord(
        record.get(str(AGENT_RUN_STEP, "id")),
        value(record.get(integer(AGENT_RUN_STEP, "sequence_no"))),
        record.get(str(AGENT_RUN_STEP, "step_name")),
        record.get(str(AGENT_RUN_STEP, "step_type")),
        record.get(str(AGENT_RUN_STEP, "status")),
        record.get(time(AGENT_RUN_STEP, "started_at")),
        record.get(time(AGENT_RUN_STEP, "finished_at")),
        value(record.get(lng(AGENT_RUN_STEP, "duration_ms"))),
        record.get(str(AGENT_RUN_STEP, "input_summary")),
        record.get(str(AGENT_RUN_STEP, "output_summary")),
        record.get(str(AGENT_RUN_STEP, "error_message")),
        record.get("metadata_json", String.class));
  }

  private static AgentEvalResultRecord toAgentEvalResultRecord(org.jooq.Record record) {
    Boolean passed = record.get(bool(AGENT_EVAL_RESULT, "passed"));
    return new AgentEvalResultRecord(
        record.get(str(AGENT_EVAL_RESULT, "id")),
        record.get(str(AGENT_EVAL_RESULT, "evaluator_name")),
        record.get(str(AGENT_EVAL_RESULT, "check_name")),
        Boolean.TRUE.equals(passed),
        record.get(decimal(AGENT_EVAL_RESULT, "score")),
        record.get(str(AGENT_EVAL_RESULT, "reason")),
        record.get("details_json", String.class),
        record.get(time(AGENT_EVAL_RESULT, "created_at")));
  }

  private static int value(Integer value) {
    return value == null ? 0 : value;
  }

  private static long value(Long value) {
    return value == null ? 0L : value;
  }
}
