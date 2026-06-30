---
title: Phase4.8：持久层收口与 Repository 命名治理
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---
# Phase4.8：持久层收口与 Repository 命名治理

> 默认前提：Phase4.6 已接入 jOOQ codegen；Phase4.7 已将 `aiops-evidence / aiops-ai-client / aiops-rca` 三个核心 Repository 迁移到 `io.aegisops.persistence.jooq.Tables` generated Tables。

---

## 1. Phase4.8 目标

Phase4.8 不再继续改业务能力，而是做 **持久层收口**：

```txt
1. 删除 no-codegen 过渡层 AegisTables。
2. 将 JdbcXxxRepository 重命名为 JooqXxxRepository。
3. 禁止新代码继续导入 AegisTables。
4. 禁止核心 Repository 继续使用 Jdbc 命名前缀。
5. 保留 AegisJooq，继续作为 JSONB 值写入 helper。
6. 不改 Repository interface。
7. 不改 Service / Controller / API contract。
8. 不改 Flyway migration。
9. 不改 Python Agent。
```

Phase4.8 完成后，持久层演进链路收口为：

```txt
JdbcTemplate 字符串 SQL
  -> Phase4.5 no-codegen jOOQ
  -> Phase4.6 generated Tables
  -> Phase4.7 AI/RCA/Evidence generated Tables
  -> Phase4.8 删除过渡层 + 命名治理
```

---

## 2. 修改文件清单

```txt
删除：
modules/aiops-persistence/src/main/java/io/aegisops/persistence/AegisTables.java
modules/aiops-persistence/src/test/java/io/aegisops/persistence/AegisTablesTest.java

删除或重命名：
modules/aiops-ai-client/src/main/java/io/aegisops/ai/client/JdbcAiRepository.java
modules/aiops-rca/src/main/java/io/aegisops/rca/JdbcRcaRepository.java
modules/aiops-evidence/src/main/java/io/aegisops/evidence/JdbcEvidenceRepository.java

新增：
modules/aiops-ai-client/src/main/java/io/aegisops/ai/client/JooqAiRepository.java
modules/aiops-rca/src/main/java/io/aegisops/rca/JooqRcaRepository.java
modules/aiops-evidence/src/main/java/io/aegisops/evidence/JooqEvidenceRepository.java

新增测试：
modules/aiops-persistence/src/test/java/io/aegisops/persistence/PersistenceArchitectureTest.java
modules/aiops-ai-client/src/test/java/io/aegisops/ai/client/AiPersistenceArchitectureTest.java
modules/aiops-ai-client/src/test/java/io/aegisops/ai/client/JooqAiRepositoryGeneratedSqlTest.java
modules/aiops-rca/src/test/java/io/aegisops/rca/RcaPersistenceArchitectureTest.java
modules/aiops-rca/src/test/java/io/aegisops/rca/JooqRcaRepositoryGeneratedSqlTest.java
modules/aiops-evidence/src/test/java/io/aegisops/evidence/EvidencePersistenceArchitectureTest.java
modules/aiops-evidence/src/test/java/io/aegisops/evidence/JooqEvidenceRepositoryGeneratedSqlTest.java

新增文档：
docs/mvp/design/phase4.8-persistence-cleanup.md
```

---

## 3. 删除 `AegisTables`

删除文件：

```txt
modules/aiops-persistence/src/main/java/io/aegisops/persistence/AegisTables.java
```

保留：

```txt
modules/aiops-persistence/src/main/java/io/aegisops/persistence/AegisJooq.java
```

`AegisJooq` 继续用于：

```java
jsonbValue(...)
jsonbArrayValue(...)
jsonObjectOrEmpty(...)
jsonArrayOrEmpty(...)
```

---

## 4. 新增 `JooqEvidenceRepository.java`

路径：

```txt
modules/aiops-evidence/src/main/java/io/aegisops/evidence/JooqEvidenceRepository.java
```

```java
package io.aegisops.evidence;

import static io.aegisops.persistence.jooq.Tables.CHANGE_EVENT;
import static io.aegisops.persistence.jooq.Tables.LOG_EVENT;

import io.aegisops.evidence.dto.ChangeEvidence;
import io.aegisops.evidence.dto.ChangeEvidenceEvent;
import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.LogEvidence;
import io.aegisops.evidence.dto.LogPattern;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record5;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** jOOQ generated Tables based evidence repository. */
@Repository
public class JooqEvidenceRepository implements EvidenceRepository {
  private final DSLContext dsl;

  public JooqEvidenceRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public LogEvidence queryLogs(EvidenceQueryRequest request, int maxPatterns) {
    if (isBlank(request.primaryAssetId()) && request.normalizedServiceNames().isEmpty()) {
      return LogEvidence.unavailable("Primary asset id and service names are empty.");
    }

    Field<String> severity = LOG_EVENT.SEVERITY;
    Field<String> sample = DSL.min(LOG_EVENT.MESSAGE).as("sample");
    Field<Integer> logCount = DSL.count().as("log_count");
    Field<OffsetDateTime> firstSeenAt = DSL.min(LOG_EVENT.OCCURRED_AT).as("first_seen_at");
    Field<OffsetDateTime> lastSeenAt = DSL.max(LOG_EVENT.OCCURRED_AT).as("last_seen_at");

    List<LogPattern> patterns =
        dsl.select(severity, sample, logCount, firstSeenAt, lastSeenAt)
            .from(LOG_EVENT)
            .where(baseLogCondition(request))
            .groupBy(severity, DSL.field("left({0}, 160)", String.class, LOG_EVENT.MESSAGE))
            .orderBy(logCount.desc(), lastSeenAt.desc())
            .limit(maxPatterns)
            .fetch(this::toLogPattern);

    if (patterns.isEmpty()) {
      return LogEvidence.unavailable("No error log evidence found.");
    }

    return new LogEvidence(true, "", patterns);
  }

  @Override
  public ChangeEvidence queryChanges(EvidenceQueryRequest request, int maxChanges) {
    if (isBlank(request.primaryAssetId()) && request.normalizedServiceNames().isEmpty()) {
      return ChangeEvidence.unavailable("Primary asset id and service names are empty.");
    }

    List<ChangeEvidenceEvent> events =
        dsl.select(
                CHANGE_EVENT.ID,
                CHANGE_EVENT.CHANGE_TYPE,
                CHANGE_EVENT.TITLE,
                CHANGE_EVENT.DESCRIPTION,
                CHANGE_EVENT.SOURCE,
                CHANGE_EVENT.OPERATOR,
                CHANGE_EVENT.RISK_LEVEL,
                CHANGE_EVENT.OCCURRED_AT)
            .from(CHANGE_EVENT)
            .where(baseChangeCondition(request))
            .orderBy(CHANGE_EVENT.OCCURRED_AT.desc())
            .limit(maxChanges)
            .fetch(
                record ->
                    new ChangeEvidenceEvent(
                        record.get(CHANGE_EVENT.ID),
                        record.get(CHANGE_EVENT.CHANGE_TYPE),
                        record.get(CHANGE_EVENT.TITLE),
                        record.get(CHANGE_EVENT.DESCRIPTION),
                        record.get(CHANGE_EVENT.SOURCE),
                        record.get(CHANGE_EVENT.OPERATOR),
                        record.get(CHANGE_EVENT.RISK_LEVEL),
                        record.get(CHANGE_EVENT.OCCURRED_AT)));

    if (events.isEmpty()) {
      return ChangeEvidence.unavailable("No change evidence found.");
    }

    return new ChangeEvidence(true, "", events);
  }

  private LogPattern toLogPattern(Record5<String, String, Integer, OffsetDateTime, OffsetDateTime> record) {
    return new LogPattern(
        record.value1(),
        record.value2(),
        numberAsLong(record.value3()),
        record.value4(),
        record.value5());
  }

  private Condition baseLogCondition(EvidenceQueryRequest request) {
    return LOG_EVENT
        .TENANT_ID
        .eq(request.tenantId())
        .and(LOG_EVENT.OCCURRED_AT.ge(request.startedAt()))
        .and(LOG_EVENT.OCCURRED_AT.le(request.lastSeenAt()))
        .and(LOG_EVENT.SEVERITY.in("error", "fatal", "critical", "warn", "warning"))
        .and(logEntityCondition(request));
  }

  private Condition baseChangeCondition(EvidenceQueryRequest request) {
    return CHANGE_EVENT
        .TENANT_ID
        .eq(request.tenantId())
        .and(CHANGE_EVENT.OCCURRED_AT.ge(request.startedAt()))
        .and(CHANGE_EVENT.OCCURRED_AT.le(request.lastSeenAt()))
        .and(changeEntityCondition(request));
  }

  private Condition logEntityCondition(EvidenceQueryRequest request) {
    Condition condition = DSL.falseCondition();

    if (!isBlank(request.primaryAssetId())) {
      condition = condition.or(LOG_EVENT.ASSET_ID.eq(request.primaryAssetId()));
    }

    if (!request.normalizedServiceNames().isEmpty()) {
      condition = condition.or(LOG_EVENT.SERVICE_NAME.in(request.normalizedServiceNames()));
    }

    return condition;
  }

  private Condition changeEntityCondition(EvidenceQueryRequest request) {
    Condition condition = DSL.falseCondition();

    if (!isBlank(request.primaryAssetId())) {
      condition = condition.or(CHANGE_EVENT.ASSET_ID.eq(request.primaryAssetId()));
    }

    if (!request.normalizedServiceNames().isEmpty()) {
      condition = condition.or(CHANGE_EVENT.SERVICE_NAME.in(request.normalizedServiceNames()));
    }

    return condition;
  }

  private static long numberAsLong(Number value) {
    return value == null ? 0L : value.longValue();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
```

删除旧文件：

```txt
modules/aiops-evidence/src/main/java/io/aegisops/evidence/JdbcEvidenceRepository.java
```

---

## 5. 新增 `JooqAiRepository.java`

路径：

```txt
modules/aiops-ai-client/src/main/java/io/aegisops/ai/client/JooqAiRepository.java
```

```java
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

/** jOOQ generated Tables based repository for the AI client. */
@Repository
public class JooqAiRepository implements AiRepository {
  private final DSLContext dsl;

  public JooqAiRepository(DSLContext dsl) {
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
        .fetchOptional(JooqAiRepository::toAiIncidentRecord);
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
        .fetch(JooqAiRepository::toAiAlertRecord);
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
        .fetchOptional(JooqAiRepository::toAiRcaRecord);
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
        .fetchOptional(JooqAiRepository::toAgentRunRecord);
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
        .fetch(JooqAiRepository::toAgentRunStepRecord);
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
        .fetch(JooqAiRepository::toAgentEvalResultRecord);
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

    return query.fetchOptional(JooqAiRepository::toAiDiagnosisRecord);
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

删除旧文件：

```txt
modules/aiops-ai-client/src/main/java/io/aegisops/ai/client/JdbcAiRepository.java
```

---

## 6. 新增 `JooqRcaRepository.java`

路径：

```txt
modules/aiops-rca/src/main/java/io/aegisops/rca/JooqRcaRepository.java
```

```java
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
public class JooqRcaRepository implements RcaRepository {
  private final DSLContext dsl;

  public JooqRcaRepository(DSLContext dsl) {
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
        .fetchOptional(JooqRcaRepository::toIncidentRecord);
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
        .fetch(JooqRcaRepository::toAlertRecord);
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
        .fetch(JooqRcaRepository::toAssetRelationRecord);
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

    return query.fetchOptional(JooqRcaRepository::toAnalysisRecord);
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

删除旧文件：

```txt
modules/aiops-rca/src/main/java/io/aegisops/rca/JdbcRcaRepository.java
```

---

## 7. 架构约束测试

### 7.1 `PersistenceArchitectureTest.java`

路径：

```txt
modules/aiops-persistence/src/test/java/io/aegisops/persistence/PersistenceArchitectureTest.java
```

```java
package io.aegisops.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PersistenceArchitectureTest {
  @Test
  void aegisTablesTransitionLayerHasBeenRemoved() {
    assertFalse(
        Files.exists(Path.of("src/main/java/io/aegisops/persistence/AegisTables.java")),
        "AegisTables should be removed after generated Tables migration.");
  }
}
```

---

### 7.2 `AiPersistenceArchitectureTest.java`

路径：

```txt
modules/aiops-ai-client/src/test/java/io/aegisops/ai/client/AiPersistenceArchitectureTest.java
```

```java
package io.aegisops.ai.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiPersistenceArchitectureTest {
  @Test
  void mainSourcesDoNotImportAegisTablesOrUseJdbcRepositoryNames() throws IOException {
    List<Path> violations =
        Files.walk(Path.of("src/main/java"))
            .filter(path -> path.toString().endsWith(".java"))
            .filter(this::isViolation)
            .toList();

    assertTrue(
        violations.isEmpty(),
        () -> "aiops-ai-client persistence architecture violations: " + violations);
  }

  private boolean isViolation(Path path) {
    String fileName = path.getFileName().toString();

    if (fileName.startsWith("Jdbc") && fileName.endsWith("Repository.java")) {
      return true;
    }

    try {
      String content = Files.readString(path);
      return content.contains("io.aegisops.persistence.AegisTables")
          || content.contains("AegisTables.");
    } catch (IOException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
```

---

### 7.3 `RcaPersistenceArchitectureTest.java`

路径：

```txt
modules/aiops-rca/src/test/java/io/aegisops/rca/RcaPersistenceArchitectureTest.java
```

```java
package io.aegisops.rca;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RcaPersistenceArchitectureTest {
  @Test
  void mainSourcesDoNotImportAegisTablesOrUseJdbcRepositoryNames() throws IOException {
    List<Path> violations =
        Files.walk(Path.of("src/main/java"))
            .filter(path -> path.toString().endsWith(".java"))
            .filter(this::isViolation)
            .toList();

    assertTrue(
        violations.isEmpty(),
        () -> "aiops-rca persistence architecture violations: " + violations);
  }

  private boolean isViolation(Path path) {
    String fileName = path.getFileName().toString();

    if (fileName.startsWith("Jdbc") && fileName.endsWith("Repository.java")) {
      return true;
    }

    try {
      String content = Files.readString(path);
      return content.contains("io.aegisops.persistence.AegisTables")
          || content.contains("AegisTables.");
    } catch (IOException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
```

---

### 7.4 `EvidencePersistenceArchitectureTest.java`

路径：

```txt
modules/aiops-evidence/src/test/java/io/aegisops/evidence/EvidencePersistenceArchitectureTest.java
```

```java
package io.aegisops.evidence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvidencePersistenceArchitectureTest {
  @Test
  void mainSourcesDoNotImportAegisTablesOrUseJdbcRepositoryNames() throws IOException {
    List<Path> violations =
        Files.walk(Path.of("src/main/java"))
            .filter(path -> path.toString().endsWith(".java"))
            .filter(this::isViolation)
            .toList();

    assertTrue(
        violations.isEmpty(),
        () -> "aiops-evidence persistence architecture violations: " + violations);
  }

  private boolean isViolation(Path path) {
    String fileName = path.getFileName().toString();

    if (fileName.startsWith("Jdbc") && fileName.endsWith("Repository.java")) {
      return true;
    }

    try {
      String content = Files.readString(path);
      return content.contains("io.aegisops.persistence.AegisTables")
          || content.contains("AegisTables.");
    } catch (IOException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
```

---

## 8. Repository SQL 测试

### 8.1 `JooqAiRepositoryGeneratedSqlTest.java`

路径：

```txt
modules/aiops-ai-client/src/test/java/io/aegisops/ai/client/JooqAiRepositoryGeneratedSqlTest.java
```

```java
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

class JooqAiRepositoryGeneratedSqlTest {
  @Test
  void saveDiagnosisUsesGeneratedTableAndJsonbCasts() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.dsl().renderInlined(context.query()));
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqAiRepository repository =
        new JooqAiRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

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

    JooqAiRepository repository =
        new JooqAiRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

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

    JooqAiRepository repository =
        new JooqAiRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

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

### 8.2 `JooqRcaRepositoryGeneratedSqlTest.java`

路径：

```txt
modules/aiops-rca/src/test/java/io/aegisops/rca/JooqRcaRepositoryGeneratedSqlTest.java
```

```java
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

class JooqRcaRepositoryGeneratedSqlTest {
  @Test
  void listAssetRelationsUsesGeneratedTablesAndInCondition() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqRcaRepository repository =
        new JooqRcaRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

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

    JooqRcaRepository repository =
        new JooqRcaRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

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

    JooqRcaRepository repository =
        new JooqRcaRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

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

### 8.3 `JooqEvidenceRepositoryGeneratedSqlTest.java`

路径：

```txt
modules/aiops-evidence/src/test/java/io/aegisops/evidence/JooqEvidenceRepositoryGeneratedSqlTest.java
```

```java
package io.aegisops.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.evidence.dto.EvidenceQueryRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class JooqEvidenceRepositoryGeneratedSqlTest {
  @Test
  void queryChangesUsesGeneratedTablesAndServiceNameInCondition() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqEvidenceRepository repository =
        new JooqEvidenceRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.queryChanges(request(), 10);

    String sql = sqlRef.get().toLowerCase();

    assertTrue(sql.contains("change_event"));
    assertTrue(sql.contains("service_name"));
    assertTrue(sql.contains("asset_id"));
    assertTrue(sql.contains("tenant_id"));
  }

  @Test
  void queryLogsMapsGeneratedResult() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());

          DSLContext dsl = DSL.using(SQLDialect.POSTGRES);

          var severity = DSL.field("severity", String.class);
          var sample = DSL.field("sample", String.class);
          var logCount = DSL.field("log_count", Integer.class);
          var firstSeenAt = DSL.field("first_seen_at", OffsetDateTime.class);
          var lastSeenAt = DSL.field("last_seen_at", OffsetDateTime.class);

          var result = dsl.newResult(severity, sample, logCount, firstSeenAt, lastSeenAt);

          result.add(
              dsl.newRecord(severity, sample, logCount, firstSeenAt, lastSeenAt)
                  .values(
                      "error",
                      "timeout",
                      3,
                      OffsetDateTime.parse("2026-06-16T09:00:00+09:00"),
                      OffsetDateTime.parse("2026-06-16T10:00:00+09:00")));

          return new MockResult[] {new MockResult(1, result)};
        };

    JooqEvidenceRepository repository =
        new JooqEvidenceRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    var result = repository.queryLogs(request(), 10);

    assertTrue(result.available());
    assertEquals(1, result.patterns().size());
    assertEquals(3L, result.patterns().get(0).count());

    String sql = sqlRef.get().toLowerCase();
    assertTrue(sql.contains("log_event"));
    assertTrue(sql.contains("log_count"));
  }

  @Test
  void queryLogsReturnsUnavailableWhenNoEntityScope() {
    JooqEvidenceRepository repository = new JooqEvidenceRepository(DSL.using(SQLDialect.POSTGRES));

    var result =
        repository.queryLogs(
            new EvidenceQueryRequest(
                "agent-diagnosis.v1",
                "tenant_1",
                "inc_1",
                "trace_1",
                null,
                OffsetDateTime.parse("2026-06-16T09:00:00+09:00"),
                OffsetDateTime.parse("2026-06-16T10:00:00+09:00"),
                List.of(),
                List.of(),
                List.of()),
            10);

    assertTrue(result.reason().contains("Primary asset id and service names are empty"));
  }

  private EvidenceQueryRequest request() {
    return new EvidenceQueryRequest(
        "agent-diagnosis.v1",
        "tenant_1",
        "inc_1",
        "trace_1",
        "asset_1",
        OffsetDateTime.parse("2026-06-16T09:00:00+09:00"),
        OffsetDateTime.parse("2026-06-16T10:00:00+09:00"),
        List.of("fp_cpu"),
        List.of("CPU high"),
        List.of("checkout-service"));
  }
}
```

删除旧测试：

```txt
modules/aiops-ai-client/src/test/java/io/aegisops/ai/client/JdbcAiRepositoryGeneratedSqlTest.java
modules/aiops-rca/src/test/java/io/aegisops/rca/JdbcRcaRepositoryGeneratedSqlTest.java
modules/aiops-evidence/src/test/java/io/aegisops/evidence/JdbcEvidenceRepositoryGeneratedSqlTest.java
```

---

## 9. 新增设计文档

路径：

```txt
docs/mvp/design/phase4.8-persistence-cleanup.md
```

```md
# Phase4.8 Persistence Cleanup

## 背景

Phase4.5 引入 no-codegen jOOQ。
Phase4.6 引入 jOOQ generated Tables 并迁移 aiops-evidence。
Phase4.7 迁移 aiops-ai-client 与 aiops-rca。

Phase4.8 收口持久层命名和过渡层。

## 目标

- 删除 AegisTables
- Repository 命名从 JdbcXxxRepository 收敛为 JooqXxxRepository
- 禁止 main source 继续导入 AegisTables
- 禁止核心 Repository 使用 Jdbc 前缀
- 保留 AegisJooq JSONB helper
- 不修改 API contract
- 不修改 Python Agent

## 删除

- io.aegisops.persistence.AegisTables
- AegisTablesTest
- JdbcAiRepository
- JdbcRcaRepository
- JdbcEvidenceRepository

## 新增

- JooqAiRepository
- JooqRcaRepository
- JooqEvidenceRepository
- Persistence architecture tests

## 验收

- main source 不存在 AegisTables import
- main source 不存在 Jdbc\*Repository.java
- apps/aiops-server -am test 通过
```

---

## 10. 验证命令

```powershell
mvn -pl modules/aiops-persistence -am generate-sources
mvn -pl modules/aiops-persistence -am test
mvn -pl modules/aiops-ai-client -am test
mvn -pl modules/aiops-rca -am test
mvn -pl modules/aiops-evidence -am test
mvn -pl apps/aiops-server -am test
```

全量：

```powershell
mvn test
```

---

## 11. 验收标准

```txt
1. AegisTables.java 已删除。
2. AegisTablesTest.java 已删除。
3. JdbcAiRepository.java 已删除。
4. JdbcRcaRepository.java 已删除。
5. JdbcEvidenceRepository.java 已删除。
6. 新增 JooqAiRepository。
7. 新增 JooqRcaRepository。
8. 新增 JooqEvidenceRepository。
9. main source 不再导入 io.aegisops.persistence.AegisTables。
10. main source 不再存在 Jdbc*Repository.java。
11. aiops-ai-client 测试通过。
12. aiops-rca 测试通过。
13. aiops-evidence 测试通过。
14. aiops-persistence generated schema 测试通过。
15. apps/aiops-server -am test 通过。
```

---

## 12. Phase4.8 完成后的状态

Phase4.8 完成后，Java 持久层进入稳定形态：

```txt
Repository interface：
  仍然保持业务抽象

Repository implementation：
  JooqAiRepository
  JooqRcaRepository
  JooqEvidenceRepository

SQL 构建：
  jOOQ generated Tables

JSONB 写入：
  AegisJooq.jsonbValue / jsonbArrayValue

禁止项：
  AegisTables
  Jdbc*Repository
  字符串拼复杂 SQL
```

下一阶段可以进入：

```txt
Phase5.0 Runbook 推荐与 AutomationPlan
Phase5.1 审批流与风险分级
Phase5.2 aiops-runner 执行器
```
