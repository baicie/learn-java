package io.aegisops.evidence;

import static io.aegisops.persistence.AegisTables.CHANGE_EVENT;
import static io.aegisops.persistence.AegisTables.LOG_EVENT;
import static io.aegisops.persistence.AegisTables.str;
import static io.aegisops.persistence.AegisTables.time;

import io.aegisops.evidence.dto.ChangeEvidence;
import io.aegisops.evidence.dto.ChangeEvidenceEvent;
import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.LogEvidence;
import io.aegisops.evidence.dto.LogPattern;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** jOOQ based evidence repository. */
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

    var severity = str(LOG_EVENT, "severity");
    var message = str(LOG_EVENT, "message");
    var occurredAt = time(LOG_EVENT, "occurred_at");
    var logCount = DSL.count().as("log_count");

    List<LogPattern> patterns =
        dsl.select(
                severity,
                DSL.min(message).as("sample"),
                logCount,
                DSL.min(occurredAt).as("first_seen_at"),
                DSL.max(occurredAt).as("last_seen_at"))
            .from(LOG_EVENT)
            .where(baseLogCondition(request))
            .groupBy(severity, DSL.field("left({0}, 160)", String.class, message))
            .orderBy(logCount.desc(), DSL.max(occurredAt).desc())
            .limit(maxPatterns)
            .fetch(
                record ->
                    new LogPattern(
                        record.get(severity),
                        record.get("sample", String.class),
                        numberAsLong(record.get("log_count")),
                        record.get("first_seen_at", OffsetDateTime.class),
                        record.get("last_seen_at", OffsetDateTime.class)));

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

    var id = str(CHANGE_EVENT, "id");
    var changeType = str(CHANGE_EVENT, "change_type");
    var title = str(CHANGE_EVENT, "title");
    var description = str(CHANGE_EVENT, "description");
    var source = str(CHANGE_EVENT, "source");
    var operator = str(CHANGE_EVENT, "operator");
    var riskLevel = str(CHANGE_EVENT, "risk_level");
    var occurredAt = time(CHANGE_EVENT, "occurred_at");

    List<ChangeEvidenceEvent> events =
        dsl.select(id, changeType, title, description, source, operator, riskLevel, occurredAt)
            .from(CHANGE_EVENT)
            .where(baseChangeCondition(request))
            .orderBy(occurredAt.desc())
            .limit(maxChanges)
            .fetch(
                record ->
                    new ChangeEvidenceEvent(
                        record.get(id),
                        record.get(changeType),
                        record.get(title),
                        record.get(description),
                        record.get(source),
                        record.get(operator),
                        record.get(riskLevel),
                        record.get(occurredAt)));

    if (events.isEmpty()) {
      return ChangeEvidence.unavailable("No change evidence found.");
    }

    return new ChangeEvidence(true, "", events);
  }

  private Condition baseLogCondition(EvidenceQueryRequest request) {
    return str(LOG_EVENT, "tenant_id")
        .eq(request.tenantId())
        .and(time(LOG_EVENT, "occurred_at").ge(request.startedAt()))
        .and(time(LOG_EVENT, "occurred_at").le(request.lastSeenAt()))
        .and(str(LOG_EVENT, "severity").in("error", "fatal", "critical", "warn", "warning"))
        .and(entityCondition(LOG_EVENT, request));
  }

  private Condition baseChangeCondition(EvidenceQueryRequest request) {
    return str(CHANGE_EVENT, "tenant_id")
        .eq(request.tenantId())
        .and(time(CHANGE_EVENT, "occurred_at").ge(request.startedAt()))
        .and(time(CHANGE_EVENT, "occurred_at").le(request.lastSeenAt()))
        .and(entityCondition(CHANGE_EVENT, request));
  }

  private Condition entityCondition(org.jooq.Table<?> table, EvidenceQueryRequest request) {
    Condition condition = DSL.falseCondition();

    if (!isBlank(request.primaryAssetId())) {
      condition = condition.or(str(table, "asset_id").eq(request.primaryAssetId()));
    }

    if (!request.normalizedServiceNames().isEmpty()) {
      condition = condition.or(str(table, "service_name").in(request.normalizedServiceNames()));
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
