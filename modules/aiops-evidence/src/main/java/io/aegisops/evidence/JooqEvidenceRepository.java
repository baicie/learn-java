package io.aegisops.evidence;

import static io.aegisops.persistence.jooq.public_.Tables.CHANGE_EVENT;
import static io.aegisops.persistence.jooq.public_.Tables.LOG_EVENT;

import io.aegisops.evidence.dto.ChangeEvidence;
import io.aegisops.evidence.dto.ChangeEvidenceEvent;
import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.LogEvidence;
import io.aegisops.evidence.dto.LogPattern;
import io.aegisops.evidence.dto.MultiSourceEvidence;
import io.aegisops.evidence.dto.SourceEvidenceItem;
import io.aegisops.evidence.dto.WebVitalSummary;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            .fetch(JooqEvidenceRepository::toLogPattern);

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
            .fetch(JooqEvidenceRepository::toChangeEvent);

    if (events.isEmpty()) {
      return ChangeEvidence.unavailable("No change evidence found.");
    }

    return new ChangeEvidence(true, "", events);
  }

  @Override
  public MultiSourceEvidence queryMultiSource(EvidenceQueryRequest request, int maxItems) {
    Condition entity = dynamicEntityCondition(request);
    List<SourceEvidenceItem> items = new java.util.ArrayList<>();
    items.addAll(queryTraceItems(request, entity, maxItems));
    items.addAll(queryMetricItems(request, entity, maxItems));
    Condition rumEntity = dynamicRumEntityCondition(request);
    items.addAll(queryRumItems(request, rumEntity, maxItems));
    long sessions = countDistinctRumField(request, rumEntity, "session_id", true);
    long pages = countDistinctRumField(request, rumEntity, "page", false);
    Map<String, WebVitalSummary> webVitals = queryWebVitals(request, rumEntity);
    items.sort(java.util.Comparator.comparing(SourceEvidenceItem::occurredAt).reversed());
    return items.isEmpty()
        ? MultiSourceEvidence.unavailable("No trace, metric, or RUM evidence found.")
        : new MultiSourceEvidence(
            true, "", items.stream().limit(maxItems).toList(), sessions, pages, webVitals);
  }

  private List<SourceEvidenceItem> queryTraceItems(
      EvidenceQueryRequest request, Condition entity, int maxItems) {
    return dsl.select(
            DSL.val("trace"), DSL.field("source_event_id", String.class),
            DSL.field("asset_id", String.class), DSL.field("service_name", String.class),
            DSL.field("trace_id", String.class), DSL.val("span"),
            DSL.val((BigDecimal) null), DSL.field("occurred_at", OffsetDateTime.class))
        .from(DSL.table("trace_event"))
        .where(dynamicBase(request).and(entity))
        .limit(maxItems)
        .fetch(JooqEvidenceRepository::toSourceEvidenceItem);
  }

  private List<SourceEvidenceItem> queryMetricItems(
      EvidenceQueryRequest request, Condition entity, int maxItems) {
    return dsl.select(
            DSL.val("metric"),
            DSL.field("source_event_id", String.class),
            DSL.field("asset_id", String.class),
            DSL.field("service_name", String.class),
            DSL.val((String) null),
            DSL.field("metric_name", String.class),
            DSL.field("metric_value", BigDecimal.class),
            DSL.field("occurred_at", OffsetDateTime.class))
        .from(DSL.table("telemetry_metric"))
        .where(dynamicBase(request).and(entity))
        .limit(maxItems)
        .fetch(JooqEvidenceRepository::toSourceEvidenceItem);
  }

  private List<SourceEvidenceItem> queryRumItems(
      EvidenceQueryRequest request, Condition entity, int maxItems) {
    return dsl.select(
            DSL.val("rum"),
            DSL.field("source_event_id", String.class),
            DSL.field("asset_id", String.class),
            DSL.val((String) null),
            DSL.field("trace_id", String.class),
            DSL.field("event_type", String.class),
            DSL.field("vital_value", BigDecimal.class),
            DSL.field("occurred_at", OffsetDateTime.class))
        .from(DSL.table("rum_event"))
        .where(dynamicBase(request).and(entity))
        .limit(maxItems)
        .fetch(JooqEvidenceRepository::toSourceEvidenceItem);
  }

  private long countDistinctRumField(
      EvidenceQueryRequest request, Condition entity, String fieldName, boolean requireNonNull) {
    Field<String> field = DSL.field(fieldName, String.class);
    Condition condition = dynamicBase(request).and(entity);
    if (requireNonNull) {
      condition = condition.and(field.isNotNull());
    }
    return dsl.select(field).from(DSL.table("rum_event")).where(condition).fetchSet(field).size();
  }

  private Map<String, WebVitalSummary> queryWebVitals(
      EvidenceQueryRequest request, Condition entity) {
    Map<String, WebVitalSummary> webVitals = new LinkedHashMap<>();
    var vitalName = DSL.field("vital_name", String.class);
    var vitalValue = DSL.field("vital_value", BigDecimal.class);
    dsl.select(
            vitalName, DSL.min(vitalValue), DSL.max(vitalValue), DSL.avg(vitalValue), DSL.count())
        .from(DSL.table("rum_event"))
        .where(dynamicBase(request).and(entity).and(vitalName.isNotNull()))
        .groupBy(vitalName)
        .fetch()
        .forEach(
            row -> {
              String name = row.value1();
              webVitals.put(
                  name,
                  new WebVitalSummary(
                      name, row.value2(), row.value3(), row.value4(), row.value5().longValue()));
            });
    return webVitals;
  }

  private Condition dynamicBase(EvidenceQueryRequest request) {
    return DSL.field("tenant_id", String.class)
        .eq(request.tenantId())
        .and(DSL.field("occurred_at", OffsetDateTime.class).ge(request.startedAt()))
        .and(DSL.field("occurred_at", OffsetDateTime.class).le(request.lastSeenAt()));
  }

  private Condition dynamicEntityCondition(EvidenceQueryRequest request) {
    Condition condition = DSL.falseCondition();
    if (!isBlank(request.primaryAssetId()))
      condition = condition.or(DSL.field("asset_id", String.class).eq(request.primaryAssetId()));
    if (!request.normalizedServiceNames().isEmpty())
      condition =
          condition.or(
              DSL.field("service_name", String.class).in(request.normalizedServiceNames()));
    return condition;
  }

  private Condition dynamicRumEntityCondition(EvidenceQueryRequest request) {
    Condition condition = DSL.falseCondition();
    if (!isBlank(request.primaryAssetId()))
      condition = condition.or(DSL.field("asset_id", String.class).eq(request.primaryAssetId()));
    if (!isBlank(request.traceId()))
      condition = condition.or(DSL.field("trace_id", String.class).eq(request.traceId()));
    return condition;
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

  private static SourceEvidenceItem toSourceEvidenceItem(
      org.jooq.Record8<String, String, String, String, String, String, BigDecimal, OffsetDateTime>
          record) {
    return new SourceEvidenceItem(
        record.value1(),
        record.value2(),
        record.value3(),
        record.value4(),
        record.value5(),
        record.value6(),
        record.value7(),
        record.value8());
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
