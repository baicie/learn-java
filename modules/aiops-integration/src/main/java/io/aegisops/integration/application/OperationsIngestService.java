package io.aegisops.integration.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.asset.api.dto.AssetUpsertCommand;
import io.aegisops.asset.application.AssetApplicationService;
import io.aegisops.asset.domain.model.AssetIdentityInput;
import io.aegisops.common.exception.AppException;
import io.aegisops.integration.api.dto.ChangeIngestRequest;
import io.aegisops.integration.api.dto.IngestBatchResponse;
import io.aegisops.integration.api.dto.IngestResponse;
import io.aegisops.integration.infrastructure.ChangePayloadMapper;
import io.aegisops.otel.application.MetricWritePort;
import io.aegisops.otel.domain.model.OtelSignal;
import io.aegisops.otel.infrastructure.adapter.OtelJsonMapper;
import io.aegisops.rum.domain.model.RumEvent;
import io.aegisops.rum.infrastructure.adapter.RumEventMapper;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationsIngestService {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final AssetApplicationService assets;
  private final OtelJsonMapper otelMapper;
  private final RumEventMapper rumMapper;
  private final MetricWritePort metricWriter;
  private final ChangePayloadMapper changeMapper;

  public OperationsIngestService(
      JdbcTemplate jdbc,
      ObjectMapper json,
      AssetApplicationService assets,
      MetricWritePort metricWriter) {
    this.jdbc = jdbc;
    this.json = json;
    this.assets = assets;
    this.otelMapper = new OtelJsonMapper(json);
    this.rumMapper = new RumEventMapper(json);
    this.metricWriter = metricWriter;
    this.changeMapper = new ChangePayloadMapper(json);
  }

  @Transactional
  public IngestResponse ingestOtel(String tenantId, String datasourceId, JsonNode payload) {
    requireDatasource(tenantId, datasourceId, Set.of("opentelemetry"));
    return ingestOtelSignal(tenantId, datasourceId, otelMapper.map(payload));
  }

  @Transactional
  public IngestBatchResponse ingestOtelBatch(
      String tenantId, String datasourceId, JsonNode payload) {
    requireDatasource(tenantId, datasourceId, Set.of("opentelemetry"));
    List<IngestResponse> results =
        otelMapper.mapAll(payload).stream()
            .map(signal -> ingestOtelSignal(tenantId, datasourceId, signal))
            .toList();
    return new IngestBatchResponse(results.size(), results);
  }

  private IngestResponse ingestOtelSignal(String tenantId, String datasourceId, OtelSignal signal) {
    String assetId = upsertService(tenantId, datasourceId, signal);
    return switch (signal.signalType()) {
      case "trace" -> insertTrace(tenantId, datasourceId, assetId, signal);
      case "log" -> insertLog(tenantId, datasourceId, assetId, signal);
      case "metric" -> insertMetric(tenantId, datasourceId, assetId, signal);
      default -> throw new AppException("OTEL_SIGNAL_INVALID", "Unsupported signalType");
    };
  }

  @Transactional
  public IngestResponse ingestRum(String tenantId, String datasourceId, JsonNode payload) {
    requireDatasource(tenantId, datasourceId, Set.of("rum"));
    RumEvent event = rumMapper.map(payload);
    var asset =
        assets.upsert(
            new AssetUpsertCommand(
                tenantId,
                "page",
                event.page(),
                event.page(),
                null,
                null,
                null,
                null,
                "normal",
                null,
                Map.of(),
                "rum",
                datasourceId,
                datasourceId,
                event.page(),
                "webhook",
                Map.of(),
                List.of(new AssetIdentityInput("url", datasourceId, event.page(), false))));
    String id = id("rum");
    int count =
        jdbc.update(
            "insert into rum_event(id,tenant_id,asset_id,datasource_id,source_event_id,event_type,page,session_id,user_hash,error_message,trace_id,vital_name,vital_value,attributes,occurred_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?) on conflict(tenant_id,datasource_id,source_event_id) do nothing",
            id,
            tenantId,
            asset.assetId(),
            datasourceId,
            event.eventId(),
            event.eventType(),
            event.page(),
            event.sessionId(),
            event.userHash(),
            event.errorMessage(),
            event.traceId(),
            event.vitalName(),
            event.vitalValue(),
            write(event.attributes()),
            event.occurredAt());
    return new IngestResponse(
        count == 1 ? id : existingId("rum_event", tenantId, datasourceId, event.eventId()),
        asset.assetId(),
        count == 1);
  }

  @Transactional
  public IngestResponse ingestChange(
      String tenantId, String datasourceId, ChangeIngestRequest request) {
    String source =
        requireDatasource(tenantId, datasourceId, Set.of("github", "gitlab", "jenkins", "webhook"));
    return ingestChange(tenantId, datasourceId, source, request);
  }

  @Transactional
  public IngestResponse ingestChangePayload(
      String tenantId, String datasourceId, JsonNode payload) {
    String source =
        requireDatasource(tenantId, datasourceId, Set.of("github", "gitlab", "jenkins", "webhook"));
    return ingestChange(tenantId, datasourceId, source, changeMapper.map(source, payload));
  }

  private IngestResponse ingestChange(
      String tenantId, String datasourceId, String source, ChangeIngestRequest request) {
    if (request == null
        || blank(request.sourceEventId())
        || blank(request.serviceName())
        || blank(request.changeType())
        || blank(request.title())
        || request.occurredAt() == null) {
      throw new AppException("CHANGE_PAYLOAD_INVALID", "Required change fields are missing");
    }
    var asset =
        assets.upsert(
            new AssetUpsertCommand(
                tenantId,
                "service",
                request.serviceName(),
                request.serviceName(),
                null,
                null,
                null,
                request.ownerTeam(),
                "normal",
                null,
                Map.of(
                    "repository", request.repositoryUrl() == null ? "" : request.repositoryUrl()),
                source,
                datasourceId,
                datasourceId,
                request.serviceName(),
                "webhook",
                request.attributes(),
                List.of()));
    String id = id("chg");
    int count =
        jdbc.update(
            "insert into change_event(id,tenant_id,asset_id,service_name,change_type,title,description,source,source_event_id,operator,risk_level,attributes,occurred_at) values (?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?) on conflict(tenant_id,source,source_event_id) where source_event_id is not null do nothing",
            id,
            tenantId,
            asset.assetId(),
            request.serviceName(),
            request.changeType(),
            request.title(),
            request.description(),
            source,
            request.sourceEventId(),
            request.operator(),
            request.riskLevel() == null ? "medium" : request.riskLevel(),
            write(request.attributes()),
            request.occurredAt());
    upsertCatalog(tenantId, asset.assetId(), request);
    for (String dependency : request.dependencyAssetIds())
      assets.upsertSourceRelation(tenantId, asset.assetId(), dependency, "depends_on", source);
    return new IngestResponse(
        count == 1 ? id : existingChangeId(tenantId, source, request.sourceEventId()),
        asset.assetId(),
        count == 1);
  }

  private String upsertService(String tenantId, String datasourceId, OtelSignal s) {
    var identities =
        s.serviceInstanceId() == null
            ? List.<AssetIdentityInput>of()
            : List.of(
                new AssetIdentityInput(
                    "otel_service_instance_id", datasourceId, s.serviceInstanceId(), true));
    return assets
        .upsert(
            new AssetUpsertCommand(
                tenantId,
                "service",
                s.serviceName(),
                s.serviceName(),
                null,
                s.environment(),
                null,
                null,
                "normal",
                null,
                Map.of("service.version", s.serviceVersion() == null ? "" : s.serviceVersion()),
                "opentelemetry",
                datasourceId,
                datasourceId,
                s.serviceInstanceId() == null ? s.serviceName() : s.serviceInstanceId(),
                "webhook",
                s.attributes(),
                identities))
        .assetId();
  }

  private IngestResponse insertTrace(String t, String d, String a, OtelSignal s) {
    String id = id("trace");
    int c =
        jdbc.update(
            "insert into trace_event(id,tenant_id,asset_id,datasource_id,source_event_id,service_name,trace_id,span_id,attributes,occurred_at) values (?,?,?,?,?,?,?,?,?::jsonb,?) on conflict(tenant_id,datasource_id,source_event_id) do nothing",
            id,
            t,
            a,
            d,
            s.sourceId(),
            s.serviceName(),
            s.traceId(),
            s.spanId(),
            write(s.attributes()),
            s.occurredAt());
    return new IngestResponse(
        c == 1 ? id : existingId("trace_event", t, d, s.sourceId()), a, c == 1);
  }

  private IngestResponse insertLog(String t, String d, String a, OtelSignal s) {
    String id = id("log");
    int c =
        jdbc.update(
            "insert into log_event(id,tenant_id,asset_id,service_name,severity,message,source,source_event_id,trace_id,attributes,occurred_at) values (?,?,?,?,?,?,?,?,?,?::jsonb,?) on conflict(tenant_id,source,source_event_id) where source_event_id is not null do nothing",
            id,
            t,
            a,
            s.serviceName(),
            s.severity() == null ? "info" : s.severity().toLowerCase(Locale.ROOT),
            s.message() == null ? "" : s.message(),
            "opentelemetry",
            s.sourceId(),
            s.traceId(),
            write(s.attributes()),
            s.occurredAt());
    return new IngestResponse(
        c == 1
            ? id
            : jdbc.queryForObject(
                "select id from log_event where tenant_id=? and source='opentelemetry' and source_event_id=?",
                String.class,
                t,
                s.sourceId()),
        a,
        c == 1);
  }

  private IngestResponse insertMetric(String t, String d, String a, OtelSignal s) {
    if (s.metricName() == null || s.metricValue() == null)
      throw new AppException("OTEL_SIGNAL_INVALID", "metricName and metricValue are required");
    String id = id("metric");
    int c =
        jdbc.update(
            "insert into telemetry_metric(id,tenant_id,asset_id,datasource_id,source_event_id,service_name,metric_name,metric_value,attributes,occurred_at) values (?,?,?,?,?,?,?,?,?::jsonb,?) on conflict(tenant_id,datasource_id,source_event_id) do nothing",
            id,
            t,
            a,
            d,
            s.sourceId(),
            s.serviceName(),
            s.metricName(),
            s.metricValue(),
            write(s.attributes()),
            s.occurredAt());
    if (c == 1) {
      metricWriter.write(t, a, s);
    }
    return new IngestResponse(
        c == 1 ? id : existingId("telemetry_metric", t, d, s.sourceId()), a, c == 1);
  }

  private void upsertCatalog(String t, String a, ChangeIngestRequest r) {
    jdbc.update(
        "insert into service_catalog(id,tenant_id,asset_id,owner_team,repository_url,runbook_id) values (?,?,?,?,?,?) on conflict(tenant_id,asset_id) do update set owner_team=excluded.owner_team,repository_url=excluded.repository_url,runbook_id=excluded.runbook_id,updated_at=now()",
        id("catalog"),
        t,
        a,
        r.ownerTeam(),
        r.repositoryUrl(),
        r.runbookId());
  }

  private String requireDatasource(String t, String d, Set<String> types) {
    List<String> rows =
        jdbc.query(
            "select type from datasource where tenant_id=? and id=?",
            (rs, n) -> rs.getString(1),
            t,
            d);
    if (rows.isEmpty() || !types.contains(rows.getFirst()))
      throw new AppException("DATASOURCE_NOT_FOUND", "Datasource not found or type mismatch");
    return rows.getFirst();
  }

  private String existingId(String table, String t, String d, String source) {
    if (!Set.of("trace_event", "telemetry_metric", "rum_event").contains(table)) {
      throw new IllegalArgumentException("Unsupported ingestion table");
    }
    return jdbc.queryForObject(
        "select id from " + table + " where tenant_id=? and datasource_id=? and source_event_id=?",
        String.class,
        t,
        d,
        source);
  }

  private String existingChangeId(String t, String source, String event) {
    return jdbc.queryForObject(
        "select id from change_event where tenant_id=? and source=? and source_event_id=?",
        String.class,
        t,
        source,
        event);
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new AppException("JSON_SERIALIZE_FAILED", "Payload is invalid");
    }
  }

  private String id(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
