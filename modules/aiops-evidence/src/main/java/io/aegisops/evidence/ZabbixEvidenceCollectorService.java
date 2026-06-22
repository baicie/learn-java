package io.aegisops.evidence;

import io.aegisops.common.exception.AppException;
import io.aegisops.zabbix.ZabbixClient;
import io.aegisops.zabbix.ZabbixClientFactory;
import io.aegisops.zabbix.ZabbixConfig;
import io.aegisops.zabbix.ZabbixEvent;
import io.aegisops.zabbix.ZabbixEventQuery;
import io.aegisops.zabbix.ZabbixHistoryPoint;
import io.aegisops.zabbix.ZabbixHistoryQuery;
import io.aegisops.zabbix.ZabbixItem;
import io.aegisops.zabbix.ZabbixItemQuery;
import io.aegisops.zabbix.ZabbixTrendPoint;
import io.aegisops.zabbix.ZabbixTrendQuery;
import io.aegisops.zabbix.ZabbixTrigger;
import io.aegisops.zabbix.ZabbixTriggerQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ZabbixEvidenceCollectorService {
  private final ZabbixEvidenceDao dao;
  private final ZabbixClientFactory zabbixClientFactory;
  private final ZabbixEvidenceAnalyzer analyzer = new ZabbixEvidenceAnalyzer();

  public ZabbixEvidenceCollectorService(
      ZabbixEvidenceDao dao, ZabbixClientFactory zabbixClientFactory) {
    this.dao = dao;
    this.zabbixClientFactory = zabbixClientFactory;
  }

  @Transactional
  public EvidenceCollectResponse collect(
      String tenantId, String incidentId, EvidenceCollectRequest request) {
    ZabbixEvidenceDao.IncidentContext incident = dao.findIncident(tenantId, incidentId);
    List<ZabbixEvidenceDao.AlertContext> alerts = dao.linkedAlerts(tenantId, incidentId);

    if (alerts.isEmpty()) {
      throw new AppException("INCIDENT_ALERTS_EMPTY", "Incident has no linked alerts");
    }

    String datasourceId = firstDatasourceId(alerts);
    String hostKey = firstHostKey(alerts);
    String service = firstLabel(alerts, "service");
    String env = firstLabel(alerts, "env");

    if (datasourceId == null || datasourceId.isBlank()) {
      throw new AppException("ZABBIX_DATASOURCE_MISSING", "Cannot infer Zabbix datasourceId");
    }

    ZabbixEvidenceDao.DataSourceRow datasource = dao.findDatasource(tenantId, datasourceId);
    ZabbixClient client = zabbixClientFactory.create(readZabbixConfig(datasource.configJson()));

    TimeRange range = timeRange(incident, request);

    List<ZabbixItem> items = fetchItems(client, hostKey);
    List<ZabbixItem> matchedItems =
        items.stream()
            .filter(item -> ZabbixEvidenceSignal.classify(item) != ZabbixEvidenceSignal.UNKNOWN)
            .toList();

    DraftBundle drafts = buildMetricDrafts(incidentId, hostKey, matchedItems, client, range);

    List<ZabbixEvent> events = fetchEvents(client, hostKey, range);
    if (!events.isEmpty()) {
      drafts.add(
          eventTimelineDraft(new EventContext(incidentId, hostKey, service, env, events), range));
    }

    List<ZabbixTrigger> triggers = fetchTriggers(client, hostKey);
    if (!triggers.isEmpty()) {
      drafts.add(
          triggerDraft(new TriggerContext(incidentId, hostKey, service, env, triggers), range));
    }

    UpsertStats stats = persistDrafts(tenantId, incidentId, drafts);

    return new EvidenceCollectResponse(
        incidentId,
        matchedItems.size(),
        drafts.historyPointCount,
        drafts.trendPointCount,
        events.size(),
        triggers.size(),
        stats.created,
        stats.updated);
  }

  public List<DiagnosisEvidenceRecord> list(String tenantId, String incidentId) {
    dao.findIncident(tenantId, incidentId);
    return dao.jdbc()
        .query(
            """
        select id, tenant_id, incident_id, evidence_key, source, evidence_type, title, summary,
               time_range_start, time_range_end, confidence, payload_json::text,
               created_at, updated_at
        from diagnosis_evidence
        where tenant_id = ? and incident_id = ?
        order by created_at asc
        """,
            (rs, rowNum) ->
                new DiagnosisEvidenceRecord(
                    rs.getString("id"),
                    rs.getString("tenant_id"),
                    rs.getString("incident_id"),
                    rs.getString("evidence_key"),
                    rs.getString("source"),
                    rs.getString("evidence_type"),
                    rs.getString("title"),
                    rs.getString("summary"),
                    rs.getObject("time_range_start", OffsetDateTime.class),
                    rs.getObject("time_range_end", OffsetDateTime.class),
                    rs.getBigDecimal("confidence"),
                    rs.getString("payload_json"),
                    rs.getObject("created_at", OffsetDateTime.class),
                    rs.getObject("updated_at", OffsetDateTime.class)),
            tenantId,
            incidentId);
  }

  private List<ZabbixItem> fetchItems(ZabbixClient client, String hostKey) {
    return client.getItems(
        new ZabbixItemQuery(hostKey == null ? List.of() : List.of(hostKey), null, null, 500));
  }

  private List<ZabbixEvent> fetchEvents(ZabbixClient client, String hostKey, TimeRange range) {
    return client.getEvents(
        new ZabbixEventQuery(
            null,
            hostKey == null ? List.of() : List.of(hostKey),
            null,
            range.from().toInstant(),
            range.to().toInstant(),
            100));
  }

  private List<ZabbixTrigger> fetchTriggers(ZabbixClient client, String hostKey) {
    return client.getTriggers(
        new ZabbixTriggerQuery(hostKey == null ? List.of() : List.of(hostKey), null, null, 100));
  }

  private DraftBundle buildMetricDrafts(
      String incidentId,
      String hostKey,
      List<ZabbixItem> matchedItems,
      ZabbixClient client,
      TimeRange range) {
    DraftBundle bundle = new DraftBundle();
    for (ZabbixItem item : matchedItems) {
      List<ZabbixHistoryPoint> history = client.getHistory(historyQuery(item, range));
      bundle.historyPointCount += history.size();

      List<ZabbixTrendPoint> trends = List.of();
      if (history.isEmpty()) {
        trends = client.getTrends(trendQuery(item, range));
        bundle.trendPointCount += trends.size();
      }

      analyzer
          .analyze(
              new ZabbixEvidenceAnalyzer.AnalysisQuery(
                  incidentId, hostKey, item, history, trends, range.from(), range.to()))
          .ifPresent(bundle::add);
    }
    return bundle;
  }

  private static ZabbixHistoryQuery historyQuery(ZabbixItem item, TimeRange range) {
    return new ZabbixHistoryQuery(
        List.of(item.itemId()),
        item.valueType(),
        range.from().toInstant(),
        range.to().toInstant(),
        5000);
  }

  private static ZabbixTrendQuery trendQuery(ZabbixItem item, TimeRange range) {
    return new ZabbixTrendQuery(
        List.of(item.itemId()), range.from().toInstant(), range.to().toInstant(), 5000);
  }

  private DiagnosisEvidenceDraft eventTimelineDraft(EventContext ctx, TimeRange range) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("incidentId", ctx.incidentId());
    payload.put("hostKey", ctx.hostKey());
    payload.put("service", ctx.service());
    payload.put("env", ctx.env());
    payload.put("eventCount", ctx.events().size());
    payload.put("events", ctx.events());

    String summary =
        "Zabbix 在该时间窗口内记录到 "
            + ctx.events().size()
            + " 条相关事件，最早事件时间："
            + ctx.events().stream()
                .map(ZabbixEvent::clock)
                .min(Comparator.naturalOrder())
                .map(Instant::toString)
                .orElse("unknown");

    return new DiagnosisEvidenceDraft(
        "zabbix:event_timeline:" + ctx.hostKey(),
        "zabbix",
        "zabbix_event_timeline",
        "Zabbix 事件时间线",
        summary,
        range.from(),
        range.to(),
        BigDecimal.valueOf(0.78),
        payload);
  }

  private DiagnosisEvidenceDraft triggerDraft(TriggerContext ctx, TimeRange range) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("incidentId", ctx.incidentId());
    payload.put("hostKey", ctx.hostKey());
    payload.put("service", ctx.service());
    payload.put("env", ctx.env());
    payload.put("triggerCount", ctx.triggers().size());
    payload.put("triggers", ctx.triggers());

    return new DiagnosisEvidenceDraft(
        "zabbix:trigger_expression:" + ctx.hostKey(),
        "zabbix",
        "zabbix_trigger_expression",
        "Zabbix Trigger 表达式",
        "Zabbix 返回 " + ctx.triggers().size() + " 条相关 Trigger，可用于解释告警触发条件",
        range.from(),
        range.to(),
        BigDecimal.valueOf(0.76),
        payload);
  }

  private UpsertStats persistDrafts(String tenantId, String incidentId, DraftBundle bundle) {
    UpsertStats stats = new UpsertStats();
    for (DiagnosisEvidenceDraft draft : bundle.drafts) {
      boolean created = dao.upsertEvidence(tenantId, incidentId, newId("evd"), draft);
      if (created) {
        stats.created++;
      } else {
        stats.updated++;
      }
    }
    return stats;
  }

  private ZabbixConfig readZabbixConfig(String configJson) {
    try {
      return dao.objectMapper().readValue(configJson, ZabbixConfig.class);
    } catch (Exception ex) {
      throw new AppException("ZABBIX_CONFIG_INVALID", "Zabbix datasource config is invalid");
    }
  }

  private String firstDatasourceId(List<ZabbixEvidenceDao.AlertContext> alerts) {
    for (ZabbixEvidenceDao.AlertContext alert : alerts) {
      String datasourceId = stringLabel(alert.labels(), "datasourceId");
      if (datasourceId != null) {
        return datasourceId;
      }

      if (alert.sourceEventId() != null && alert.sourceEventId().contains(":")) {
        return alert.sourceEventId().split(":", 2)[0];
      }
    }

    return null;
  }

  private String firstHostKey(List<ZabbixEvidenceDao.AlertContext> alerts) {
    for (ZabbixEvidenceDao.AlertContext alert : alerts) {
      String hostId = stringLabel(alert.labels(), "zabbixHostId");
      if (hostId != null) {
        return hostId;
      }

      Object hostIds = alert.labels().get("zabbixHostIds");
      if (hostIds instanceof Iterable<?> iterable) {
        for (Object item : iterable) {
          if (item != null && !String.valueOf(item).isBlank()) {
            return String.valueOf(item).trim();
          }
        }
      }

      String hostName = stringLabel(alert.labels(), "zabbixHostName");
      if (hostName != null) {
        return hostName;
      }
    }

    return null;
  }

  private String firstLabel(List<ZabbixEvidenceDao.AlertContext> alerts, String key) {
    for (ZabbixEvidenceDao.AlertContext alert : alerts) {
      String value = stringLabel(alert.labels(), key);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private String stringLabel(Map<String, Object> labels, String key) {
    if (labels == null || key == null) {
      return null;
    }

    Object value = labels.get(key);
    if (value == null || String.valueOf(value).isBlank()) {
      return null;
    }

    return String.valueOf(value).trim();
  }

  private TimeRange timeRange(
      ZabbixEvidenceDao.IncidentContext incident, EvidenceCollectRequest request) {
    EvidenceCollectRequest safeRequest =
        request == null ? new EvidenceCollectRequest(null, null, null) : request;

    OffsetDateTime to =
        safeRequest.timeTo() != null
            ? safeRequest.timeTo()
            : firstNonNull(
                incident.resolvedAt(), incident.lastSeenAt(), OffsetDateTime.now(ZoneOffset.UTC));

    OffsetDateTime from =
        safeRequest.timeFrom() != null
            ? safeRequest.timeFrom()
            : firstNonNull(incident.startedAt(), to)
                .minusMinutes(safeRequest.normalizedLookbackMinutes());

    return new TimeRange(from, to);
  }

  private OffsetDateTime firstNonNull(OffsetDateTime... values) {
    for (OffsetDateTime value : values) {
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }

  record TimeRange(OffsetDateTime from, OffsetDateTime to) {}

  private record EventContext(
      String incidentId, String hostKey, String service, String env, List<ZabbixEvent> events) {}

  private record TriggerContext(
      String incidentId,
      String hostKey,
      String service,
      String env,
      List<ZabbixTrigger> triggers) {}

  private static final class DraftBundle {
    final List<DiagnosisEvidenceDraft> drafts = new ArrayList<>();
    int historyPointCount;
    int trendPointCount;

    void add(DiagnosisEvidenceDraft draft) {
      drafts.add(draft);
    }
  }

  private static final class UpsertStats {
    int created;
    int updated;
  }
}
