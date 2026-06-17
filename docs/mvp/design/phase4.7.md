# Phase4.7：迁移 AI / RCA Repository 到 generated Tables

基于当前 `a08b348` 后的状态，Phase4.7 的目标很明确：**把 Phase4.5 里还在使用 `AegisTables` 过渡层的 `aiops-ai-client` 和 `aiops-rca` 迁移到 jOOQ generated Tables**。

当前 Phase4.6 已经有 `jooq-codegen.xml`，从 `apps/aiops-server/src/main/resources/db/migration` 读取 Flyway migration，并生成 `io.aegisops.persistence.jooq` 包。 Evidence Repository 也已经切换到了 generated Tables，例如直接静态导入 `io.aegisops.persistence.jooq.Tables.CHANGE_EVENT / LOG_EVENT`。 但 `JdbcAiRepository` 和 `JdbcRcaRepository` 仍然在使用 `AegisTables` 过渡层。

---

# 1. Phase4.7 目标

```txt id="oh5k7f"
1. JdbcAiRepository 全量迁移到 generated Tables。
2. JdbcRcaRepository 全量迁移到 generated Tables。
3. 保留 AegisJooq 作为 JSONB 值写入工具。
4. AegisTables 标记 @Deprecated，不立即删除。
5. 删除/替换旧 no-codegen SQL 测试。
6. 新增 generated Tables SQL smoke tests。
7. 不改 Service / Controller / API contract。
8. 不改 DB migration。
9. 不改 Python Agent。
```

---

# 2. 修改文件清单

```txt id="j3glir"
modules/aiops-persistence/
  src/main/java/io/aegisops/persistence/AegisTables.java

modules/aiops-ai-client/
  src/main/java/io/aegisops/ai/client/JdbcAiRepository.java
  src/test/java/io/aegisops/ai/client/JdbcAiRepositoryGeneratedSqlTest.java
  删除 src/test/java/io/aegisops/ai/client/JdbcAiRepositoryJooqSqlTest.java

modules/aiops-rca/
  src/main/java/io/aegisops/rca/JdbcRcaRepository.java
  src/test/java/io/aegisops/rca/JdbcRcaRepositoryGeneratedSqlTest.java
  删除 src/test/java/io/aegisops/rca/JdbcRcaRepositoryJooqSqlTest.java

docs/mvp/design/phase4.7-generated-repository-migration.md
```

---

# 3. 标记 `AegisTables` 为过渡层

## `modules/aiops-persistence/src/main/java/io/aegisops/persistence/AegisTables.java`

只需要修改类注释和注解，不改原有字段，降低回滚风险：

```java id="ed0jd5"
package io.aegisops.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Table;
import org.jooq.impl.DSL;

/**
 * no-codegen jOOQ 表与字段常量。
 *
 * <p>Phase4.5 过渡层。Phase4.6 起新代码应优先使用
 * {@code io.aegisops.persistence.jooq.Tables}。Phase4.7 迁移 aiops-ai-client / aiops-rca 后，
 * 本类只保留给尚未迁移的旧代码或紧急回滚使用。
 */
@Deprecated(since = "4.7", forRemoval = false)
public final class AegisTables {
  private AegisTables() {}

  public static final Table<?> INCIDENT = DSL.table(DSL.name("incident")).as("i");
  public static final Table<?> INCIDENT_EVENT = DSL.table(DSL.name("incident_event")).as("ie");
  public static final Table<?> ALERT_EVENT = DSL.table(DSL.name("alert_event")).as("a");
  public static final Table<?> RCA_ANALYSIS = DSL.table(DSL.name("rca_analysis")).as("r");
  public static final Table<?> ASSET_RELATION = DSL.table(DSL.name("asset_relation")).as("ar");
  public static final Table<?> AI_DIAGNOSIS = DSL.table(DSL.name("ai_diagnosis")).as("ad");
  public static final Table<?> INCIDENT_TIMELINE =
      DSL.table(DSL.name("incident_timeline")).as("it");
  public static final Table<?> AGENT_RUN = DSL.table(DSL.name("agent_run")).as("agr");
  public static final Table<?> AGENT_RUN_STEP = DSL.table(DSL.name("agent_run_step")).as("agrs");
  public static final Table<?> AGENT_EVAL_RESULT =
      DSL.table(DSL.name("agent_eval_result")).as("aer");
  public static final Table<?> LOG_EVENT = DSL.table(DSL.name("log_event")).as("le");
  public static final Table<?> CHANGE_EVENT = DSL.table(DSL.name("change_event")).as("ce");

  public static Field<String> str(Table<?> table, String column) {
    return DSL.field(DSL.name(table.getName(), column), String.class);
  }

  public static Field<Integer> integer(Table<?> table, String column) {
    return DSL.field(DSL.name(table.getName(), column), Integer.class);
  }

  public static Field<Long> lng(Table<?> table, String column) {
    return DSL.field(DSL.name(table.getName(), column), Long.class);
  }

  public static Field<Boolean> bool(Table<?> table, String column) {
    return DSL.field(DSL.name(table.getName(), column), Boolean.class);
  }

  public static Field<BigDecimal> decimal(Table<?> table, String column) {
    return DSL.field(DSL.name(table.getName(), column), BigDecimal.class);
  }

  public static Field<OffsetDateTime> time(Table<?> table, String column) {
    return DSL.field(DSL.name(table.getName(), column), OffsetDateTime.class);
  }

  public static Field<JSONB> jsonb(Table<?> table, String column) {
    return DSL.field(DSL.name(table.getName(), column), JSONB.class);
  }
}
```

---

# 4. 替换 `JdbcAiRepository.java`

## `modules/aiops-ai-client/src/main/java/io/aegisops/ai/client/JdbcAiRepository.java`

```java id="n7nroe"
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
 * <p>Phase4.7 将本类从 Phase4.5 的 no-codegen AegisTables 迁移到
 * {@code io.aegisops.persistence.jooq.Tables}。
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
        AI_DIAGNOSIS.TENANT_ID.eq(tenantId).and(AI_DIAGNOSIS.INCIDENT_ID.eq(incidentId)),
        true);
  }

  @Override
  public Optional<AiDiagnosisRecord> findDiagnosis(String tenantId, String diagnosisId) {
    return findDiagnosisByCondition(
        AI_DIAGNOSIS.TENANT_ID.eq(tenantId).and(AI_DIAGNOSIS.ID.eq(diagnosisId)),
        false);
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
```

---

# 5. 替换 `JdbcRcaRepository.java`

## `modules/aiops-rca/src/main/java/io/aegisops/rca/JdbcRcaRepository.java`

```java id="5ft0nt"
package io.aegisops.rca;

import static io.aegisops.persistence.AegisJooq.jsonArrayOrEmpty;
import static io.aegisops.persistence.AegisJooq.jsonbArrayValue;
import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.Tables.ALERT_EVENT;
import static io.aegisops.persistence.jooq.Tables.ASSET_RELATION;
import static io.aegisops.persistence.jooq.Tables.INCIDENT;
import static io.aegisops.persistence.jooq.Tables.INCIDENT_EVENT;
import static io.aegisops.persistence.jooq.Tables.INCIDENT_TIMELINE;
import static io.aegisops.persistence.jooq.Tables.RCA_ANALYSIS;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** jOOQ generated Tables based RCA repository. */
@Repository
public class JdbcRcaRepository implements RcaRepository {
  private final DSLContext dsl;

  public JdbcRcaRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public Optional<RcaIncidentRecord> findIncident(String tenantId, String incidentId) {
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
            INCIDENT.RESOLVED_AT,
            INCIDENT.CREATED_AT,
            INCIDENT.UPDATED_AT)
        .from(INCIDENT)
        .where(INCIDENT.TENANT_ID.eq(tenantId))
        .and(INCIDENT.ID.eq(incidentId))
        .fetchOptional(JdbcRcaRepository::toIncidentRecord);
  }

  @Override
  public List<RcaAlertRecord> listIncidentAlerts(String tenantId, String incidentId) {
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
            ALERT_EVENT.LABELS.cast(String.class).as("labels"),
            ALERT_EVENT.STARTS_AT,
            ALERT_EVENT.CREATED_AT)
        .from(INCIDENT_EVENT)
        .join(INCIDENT)
        .on(INCIDENT.ID.eq(INCIDENT_EVENT.INCIDENT_ID))
        .join(ALERT_EVENT)
        .on(ALERT_EVENT.ID.eq(INCIDENT_EVENT.EVENT_ID))
        .where(INCIDENT.TENANT_ID.eq(tenantId))
        .and(INCIDENT.ID.eq(incidentId))
        .and(INCIDENT_EVENT.EVENT_TYPE.eq("alert"))
        .and(ALERT_EVENT.TENANT_ID.eq(tenantId))
        .orderBy(ALERT_EVENT.STARTS_AT.asc())
        .fetch(JdbcRcaRepository::toAlertRecord);
  }

  @Override
  public List<RcaAssetRelationRecord> listAssetRelations(String tenantId, List<String> assetIds) {
    if (assetIds == null || assetIds.isEmpty()) {
      return List.of();
    }

    return dsl.select(
            ASSET_RELATION.ID,
            ASSET_RELATION.FROM_ASSET_ID,
            ASSET_RELATION.TO_ASSET_ID,
            ASSET_RELATION.RELATION_TYPE,
            ASSET_RELATION.CONFIDENCE,
            ASSET_RELATION.SOURCE)
        .from(ASSET_RELATION)
        .where(ASSET_RELATION.TENANT_ID.eq(tenantId))
        .and(
            ASSET_RELATION
                .FROM_ASSET_ID
                .in(assetIds)
                .or(ASSET_RELATION.TO_ASSET_ID.in(assetIds)))
        .orderBy(ASSET_RELATION.CONFIDENCE.desc())
        .fetch(JdbcRcaRepository::toAssetRelationRecord);
  }

  @Override
  public Optional<RcaAnalysisRecord> findLatestAnalysis(String tenantId, String incidentId) {
    return findAnalysisByCondition(
        RCA_ANALYSIS.TENANT_ID.eq(tenantId).and(RCA_ANALYSIS.INCIDENT_ID.eq(incidentId)),
        true);
  }

  @Override
  public void saveAnalysis(
      String id,
      String tenantId,
      String incidentId,
      String suspectedRootCause,
      BigDecimal confidence,
      String summary,
      String evidenceJson,
      String modelVersion) {
    dsl.insertInto(RCA_ANALYSIS)
        .set(RCA_ANALYSIS.ID, id)
        .set(RCA_ANALYSIS.TENANT_ID, tenantId)
        .set(RCA_ANALYSIS.INCIDENT_ID, incidentId)
        .set(RCA_ANALYSIS.STATUS, "completed")
        .set(RCA_ANALYSIS.SUSPECTED_ROOT_CAUSE, suspectedRootCause)
        .set(RCA_ANALYSIS.CONFIDENCE, confidence)
        .set(RCA_ANALYSIS.SUMMARY, summary)
        .set(RCA_ANALYSIS.EVIDENCE, jsonbArrayValue(jsonArrayOrEmpty(evidenceJson)))
        .set(RCA_ANALYSIS.MODEL_VERSION, modelVersion)
        .set(RCA_ANALYSIS.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public Optional<RcaAnalysisRecord> findAnalysis(String tenantId, String id) {
    return findAnalysisByCondition(
        RCA_ANALYSIS.TENANT_ID.eq(tenantId).and(RCA_ANALYSIS.ID.eq(id)), false);
  }

  @Override
  public void updateIncidentRca(
      String tenantId, String incidentId, String suspectedRootCause, BigDecimal confidence) {
    dsl.update(INCIDENT)
        .set(INCIDENT.SUSPECTED_ROOT_CAUSE, suspectedRootCause)
        .set(INCIDENT.CONFIDENCE, confidence)
        .set(INCIDENT.UPDATED_AT, DSL.currentOffsetDateTime())
        .where(INCIDENT.TENANT_ID.eq(tenantId))
        .and(INCIDENT.ID.eq(incidentId))
        .execute();
  }

  @Override
  public void addIncidentTimeline(
      String id,
      String incidentId,
      OffsetDateTime eventTime,
      String title,
      String description,
      String payloadJson) {
    dsl.insertInto(INCIDENT_TIMELINE)
        .set(INCIDENT_TIMELINE.ID, id)
        .set(INCIDENT_TIMELINE.INCIDENT_ID, incidentId)
        .set(INCIDENT_TIMELINE.EVENT_TIME, eventTime)
        .set(INCIDENT_TIMELINE.EVENT_TYPE, "rca_analyzed")
        .set(INCIDENT_TIMELINE.TITLE, title)
        .set(INCIDENT_TIMELINE.DESCRIPTION, description)
        .set(INCIDENT_TIMELINE.SOURCE, "system")
        .set(INCIDENT_TIMELINE.PAYLOAD, jsonbValue(payloadJson))
        .execute();
  }

  private Optional<RcaAnalysisRecord> findAnalysisByCondition(Condition condition, boolean latest) {
    var query =
        dsl.select(
                RCA_ANALYSIS.ID,
                RCA_ANALYSIS.TENANT_ID,
                RCA_ANALYSIS.INCIDENT_ID,
                RCA_ANALYSIS.STATUS,
                RCA_ANALYSIS.SUSPECTED_ROOT_CAUSE,
                RCA_ANALYSIS.CONFIDENCE,
                RCA_ANALYSIS.SUMMARY,
                RCA_ANALYSIS.EVIDENCE.cast(String.class).as("evidence"),
                RCA_ANALYSIS.MODEL_VERSION,
                RCA_ANALYSIS.CREATED_AT)
            .from(RCA_ANALYSIS)
            .where(condition)
            .orderBy(latest ? RCA_ANALYSIS.CREATED_AT.desc() : RCA_ANALYSIS.ID.asc())
            .limit(1);

    return query.fetchOptional(JdbcRcaRepository::toAnalysisRecord);
  }

  private static RcaIncidentRecord toIncidentRecord(org.jooq.Record record) {
    return new RcaIncidentRecord(
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
        record.get(INCIDENT.RESOLVED_AT),
        record.get(INCIDENT.CREATED_AT),
        record.get(INCIDENT.UPDATED_AT));
  }

  private static RcaAlertRecord toAlertRecord(org.jooq.Record record) {
    return new RcaAlertRecord(
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
        record.get("labels", String.class),
        record.get(ALERT_EVENT.STARTS_AT),
        record.get(ALERT_EVENT.CREATED_AT));
  }

  private static RcaAssetRelationRecord toAssetRelationRecord(org.jooq.Record record) {
    return new RcaAssetRelationRecord(
        record.get(ASSET_RELATION.ID),
        record.get(ASSET_RELATION.FROM_ASSET_ID),
        record.get(ASSET_RELATION.TO_ASSET_ID),
        record.get(ASSET_RELATION.RELATION_TYPE),
        record.get(ASSET_RELATION.CONFIDENCE),
        record.get(ASSET_RELATION.SOURCE));
  }

  private static RcaAnalysisRecord toAnalysisRecord(org.jooq.Record record) {
    return new RcaAnalysisRecord(
        record.get(RCA_ANALYSIS.ID),
        record.get(RCA_ANALYSIS.TENANT_ID),
        record.get(RCA_ANALYSIS.INCIDENT_ID),
        record.get(RCA_ANALYSIS.STATUS),
        record.get(RCA_ANALYSIS.SUSPECTED_ROOT_CAUSE),
        record.get(RCA_ANALYSIS.CONFIDENCE),
        record.get(RCA_ANALYSIS.SUMMARY),
        record.get("evidence", String.class),
        record.get(RCA_ANALYSIS.MODEL_VERSION),
        record.get(RCA_ANALYSIS.CREATED_AT));
  }

  private static int value(Integer value) {
    return value == null ? 0 : value;
  }
}
```

---

# 6. 替换 AI Repository 测试

删除旧文件：

```txt id="21vzwl"
modules/aiops-ai-client/src/test/java/io/aegisops/ai/client/JdbcAiRepositoryJooqSqlTest.java
```

新增：

## `modules/aiops-ai-client/src/test/java/io/aegisops/ai/client/JdbcAiRepositoryGeneratedSqlTest.java`

```java id="ekqau6"
package io.aegisops.ai.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import io.aegisops.ai.client.dto.SaveDiagnosisCommand;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class JdbcAiRepositoryGeneratedSqlTest {
  @Test
  void saveDiagnosisUsesGeneratedTableAndJsonbCasts() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.dsl().renderInlined(context.query()));
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JdbcAiRepository repository =
        new JdbcAiRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.saveDiagnosis(
        new SaveDiagnosisCommand(
            "diag_1",
            "tenant_1",
            "inc_1",
            new AgentDiagnosisResponse(
                "agent-diagnosis.v1",
                "aiops-agent",
                "langgraph-deterministic",
                "aegisops_diagnosis_graph",
                "summary",
                "root",
                "impact",
                List.of("step"),
                List.of(),
                List.of(),
                Map.of()),
            "{}",
            "{}",
            "[\"step\"]",
            "[]",
            "[]"));

    String sql = sqlRef.get().toLowerCase();

    assertTrue(sql.contains("insert into"));
    assertTrue(sql.contains("ai_diagnosis"));
    assertTrue(sql.contains("request_payload"));
    assertTrue(sql.contains("response_raw"));
    assertTrue(sql.contains("::jsonb"));
  }

  @Test
  void listIncidentAlertsRendersGeneratedJoinAndTenantGuard() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JdbcAiRepository repository =
        new JdbcAiRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.listIncidentAlerts("tenant_1", "inc_1");

    String sql = sqlRef.get().toLowerCase();

    assertTrue(sql.contains("incident_event"));
    assertTrue(sql.contains("incident"));
    assertTrue(sql.contains("alert_event"));
    assertTrue(sql.contains("tenant_id"));
    assertTrue(sql.contains("event_type"));
  }

  @Test
  void findLatestAgentRunRendersAgentRunQuery() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JdbcAiRepository repository =
        new JdbcAiRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.findLatestAgentRun("tenant_1", "inc_1");

    String sql = sqlRef.get().toLowerCase();

    assertTrue(sql.contains("agent_run"));
    assertTrue(sql.contains("tenant_id"));
    assertTrue(sql.contains("incident_id"));
    assertTrue(sql.contains("created_at"));
  }
}
```

---

# 7. 替换 RCA Repository 测试

删除旧文件：

```txt id="wrpfc8"
modules/aiops-rca/src/test/java/io/aegisops/rca/JdbcRcaRepositoryJooqSqlTest.java
```

新增：

## `modules/aiops-rca/src/test/java/io/aegisops/rca/JdbcRcaRepositoryGeneratedSqlTest.java`

```java id="7qjx19"
package io.aegisops.rca;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class JdbcRcaRepositoryGeneratedSqlTest {
  @Test
  void listAssetRelationsUsesGeneratedTablesAndInCondition() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JdbcRcaRepository repository =
        new JdbcRcaRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.listAssetRelations("tenant_1", List.of("asset_1", "asset_2"));

    String sql = sqlRef.get().toLowerCase();

    assertTrue(sql.contains("asset_relation"));
    assertTrue(sql.contains("from_asset_id"));
    assertTrue(sql.contains("to_asset_id"));
    assertTrue(sql.contains(" in "));
  }

  @Test
  void saveAnalysisUsesJsonbArrayForEvidence() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.dsl().renderInlined(context.query()));
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JdbcRcaRepository repository =
        new JdbcRcaRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.saveAnalysis(
        "rca_1",
        "tenant_1",
        "inc_1",
        "root",
        BigDecimal.valueOf(0.8),
        "summary",
        "",
        "rules-v1");

    String sql = sqlRef.get().toLowerCase();

    assertTrue(sql.contains("insert into"));
    assertTrue(sql.contains("rca_analysis"));
    assertTrue(sql.contains("evidence"));
    assertTrue(sql.contains("[]"));
    assertTrue(sql.contains("::jsonb"));
  }

  @Test
  void listIncidentAlertsRendersGeneratedJoinAndTenantGuard() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JdbcRcaRepository repository =
        new JdbcRcaRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.listIncidentAlerts("tenant_1", "inc_1");

    String sql = sqlRef.get().toLowerCase();

    assertTrue(sql.contains("incident_event"));
    assertTrue(sql.contains("incident"));
    assertTrue(sql.contains("alert_event"));
    assertTrue(sql.contains("tenant_id"));
    assertTrue(sql.contains("event_type"));
  }
}
```

---

# 8. 增加 generated coverage 测试

## `modules/aiops-persistence/src/test/java/io/aegisops/persistence/JooqGeneratedRepositoryCoverageTest.java`

```java id="zva6rc"
package io.aegisops.persistence;

import static io.aegisops.persistence.jooq.Tables.AGENT_EVAL_RESULT;
import static io.aegisops.persistence.jooq.Tables.AGENT_RUN;
import static io.aegisops.persistence.jooq.Tables.AGENT_RUN_STEP;
import static io.aegisops.persistence.jooq.Tables.AI_DIAGNOSIS;
import static io.aegisops.persistence.jooq.Tables.ALERT_EVENT;
import static io.aegisops.persistence.jooq.Tables.ASSET_RELATION;
import static io.aegisops.persistence.jooq.Tables.CHANGE_EVENT;
import static io.aegisops.persistence.jooq.Tables.INCIDENT;
import static io.aegisops.persistence.jooq.Tables.INCIDENT_EVENT;
import static io.aegisops.persistence.jooq.Tables.INCIDENT_TIMELINE;
import static io.aegisops.persistence.jooq.Tables.LOG_EVENT;
import static io.aegisops.persistence.jooq.Tables.RCA_ANALYSIS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.jooq.JSONB;
import org.junit.jupiter.api.Test;

class JooqGeneratedRepositoryCoverageTest {
  @Test
  void generatedTablesCoverAiRepositoryTables() {
    assertNotNull(INCIDENT);
    assertNotNull(INCIDENT_EVENT);
    assertNotNull(ALERT_EVENT);
    assertNotNull(RCA_ANALYSIS);
    assertNotNull(AI_DIAGNOSIS);
    assertNotNull(INCIDENT_TIMELINE);
    assertNotNull(AGENT_RUN);
    assertNotNull(AGENT_RUN_STEP);
    assertNotNull(AGENT_EVAL_RESULT);
  }

  @Test
  void generatedTablesCoverRcaAndEvidenceTables() {
    assertNotNull(ASSET_RELATION);
    assertNotNull(LOG_EVENT);
    assertNotNull(CHANGE_EVENT);
  }

  @Test
  void generatedJsonColumnsRemainJsonb() {
    assertEquals(JSONB.class, ALERT_EVENT.LABELS.getType());
    assertEquals(JSONB.class, RCA_ANALYSIS.EVIDENCE.getType());
    assertEquals(JSONB.class, AI_DIAGNOSIS.REQUEST_PAYLOAD.getType());
    assertEquals(JSONB.class, AI_DIAGNOSIS.RESPONSE_RAW.getType());
    assertEquals(JSONB.class, AI_DIAGNOSIS.NEXT_STEPS.getType());
    assertEquals(JSONB.class, AGENT_RUN.SAFETY.getType());
    assertEquals(JSONB.class, AGENT_RUN_STEP.METADATA.getType());
    assertEquals(JSONB.class, AGENT_EVAL_RESULT.DETAILS.getType());
    assertEquals(JSONB.class, LOG_EVENT.ATTRIBUTES.getType());
    assertEquals(JSONB.class, CHANGE_EVENT.ATTRIBUTES.getType());
  }
}
```

---

# 9. 新增设计文档

## `docs/mvp/design/phase4.7-generated-repository-migration.md`

```md id="nkbdbx"
# Phase4.7 Generated Repository Migration

## 背景

Phase4.6 已经接入 jOOQ codegen，并将 aiops-evidence 迁移到 generated Tables。

Phase4.7 继续迁移：

- aiops-ai-client
- aiops-rca

## 目标

- JdbcAiRepository 使用 io.aegisops.persistence.jooq.Tables
- JdbcRcaRepository 使用 io.aegisops.persistence.jooq.Tables
- AegisTables 标记 Deprecated
- 不修改 Repository interface
- 不修改 Service / Controller
- 不修改 API contract
- 不修改 Python Agent

## 不做

- 不删除 AegisTables
- 不引入 POJO / DAO
- 不修改 Flyway schema
- 不做业务逻辑改造

## 后续

Phase4.8 可以删除 AegisTables，或者先增加 lint/checkstyle 规则禁止新代码导入 AegisTables。
```

---

# 10. 验证命令

```powershell id="fx8s8d"
mvn -pl modules/aiops-persistence -am generate-sources
mvn -pl modules/aiops-persistence -am test
mvn -pl modules/aiops-ai-client -am test
mvn -pl modules/aiops-rca -am test
mvn -pl modules/aiops-evidence -am test
mvn -pl apps/aiops-server -am test
```

全量：

```powershell id="x2c9b9"
mvn test
```

---

# 11. 验收标准

```txt id="xidg95"
1. JdbcAiRepository 不再 import io.aegisops.persistence.AegisTables。
2. JdbcRcaRepository 不再 import io.aegisops.persistence.AegisTables。
3. JdbcEvidenceRepository 保持 generated Tables。
4. AegisTables 标记 @Deprecated。
5. aiops-persistence generated schema 测试通过。
6. aiops-ai-client generated SQL 测试通过。
7. aiops-rca generated SQL 测试通过。
8. apps/aiops-server -am test 通过。
9. API contract 不变。
10. Python Agent 不变。
```

---

# 12. Phase4.8 建议

Phase4.7 完成后，Phase4.8 可以做持久层收口：

```txt id="yib108"
Phase4.8：
  删除或冻结 AegisTables
  增加 forbidden import 检查
  将 JdbcXxxRepository 重命名为 JooqXxxRepository
  增加 Repository integration test profile
```

Phase4.7 完成后，AegisOps 的 Java 持久层基本完成了从：

```txt id="kejdjz"
JdbcTemplate 字符串 SQL
  -> no-codegen jOOQ
  -> generated Tables jOOQ
```

这一轮过渡。
