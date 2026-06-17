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
public class JdbcEvidenceRepository implements EvidenceRepository {
  private final DSLContext dsl;

  public JdbcEvidenceRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public LogEvidence queryLogs(EvidenceQueryRequest request, int maxPatterns) {
    if (isBlank(request.primaryAssetId()) && request.normalizedServiceNames().isEmpty()) {
      return LogEvidence.unavailable("Primary asset id and service names are empty.");
    }

    Field<String> severity = LOG_EVENT.SEVERITY;
    Field<OffsetDateTime> occurredAt = LOG_EVENT.OCCURRED_AT;
    Field<Integer> logCount = DSL.count().as("log_count");

    List<LogPattern> patterns =
        dsl.select(
                severity,
                DSL.min(LOG_EVENT.MESSAGE).as("sample"),
                logCount,
                DSL.min(occurredAt).as("first_seen_at"),
                DSL.max(occurredAt).as("last_seen_at"))
            .from(LOG_EVENT)
            .where(baseLogCondition(request))
            .groupBy(severity, DSL.field("left({0}, 160)", String.class, LOG_EVENT.MESSAGE))
            .orderBy(logCount.desc(), DSL.max(occurredAt).desc())
            .limit(maxPatterns)
            .fetch(JdbcEvidenceRepository::toLogPattern);

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
            .fetch(JdbcEvidenceRepository::toChangeEvent);

    if (events.isEmpty()) {
      return ChangeEvidence.unavailable("No change evidence found.");
    }

    return new ChangeEvidence(true, "", events);
  }

  private static LogPattern toLogPattern(
      Record5<String, String, Integer, OffsetDateTime, OffsetDateTime> record) {
    return new LogPattern(
        record.value1(),
        record.value2(),
        numberAsLong(record.value3()),
        record.value4(),
        record.value5());
  }

  private static ChangeEvidenceEvent toChangeEvent(
      org.jooq.Record8<String, String, String, String, String, String, String, OffsetDateTime>
          record) {
    return new ChangeEvidenceEvent(
        record.value1(),
        record.value2(),
        record.value3(),
        record.value4(),
        record.value5(),
        record.value6(),
        record.value7(),
        record.value8());
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

  private static long numberAsLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }

    if (value == null) {
      return 0L;
    }

    return Long.parseLong(String.valueOf(value));
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
