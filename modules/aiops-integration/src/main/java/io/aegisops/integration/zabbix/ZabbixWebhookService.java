package io.aegisops.integration.zabbix;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.common.outbox.OutboxWriter;
import io.aegisops.datasource.zabbix.ZabbixExternalIds;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ZabbixWebhookService {
  private static final String OUTBOX_TARGET_APP = "worker";
  private static final String OUTBOX_JOB_NAME = "incident-aggregate";

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final ZabbixWebhookTokenVerifier tokenVerifier;
  private final ZabbixWebhookMapper mapper;
  private final OutboxWriter outboxWriter;

  public ZabbixWebhookService(
      JdbcTemplate jdbc,
      ObjectMapper objectMapper,
      ZabbixWebhookTokenVerifier tokenVerifier,
      ZabbixWebhookMapper mapper,
      OutboxWriter outboxWriter) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.tokenVerifier = tokenVerifier;
    this.mapper = mapper;
    this.outboxWriter = outboxWriter;
  }

  public ZabbixWebhookIngestResponse ingest(
      String datasourceId, String token, ZabbixWebhookPayload payload) {
    if (!tokenVerifier.verify(token)) {
      throw new AppException("ZABBIX_WEBHOOK_UNAUTHORIZED", "Invalid Zabbix webhook token");
    }

    ZabbixWebhookAlertMapping mapping = mapper.map(datasourceId, payload);
    DataSourceBinding datasource = getDatasourceBinding(mapping.datasourceId());
    String assetId = resolveAssetId(datasource.tenantId(), mapping);

    return upsertAlert(datasource, mapping, assetId);
  }

  /**
   * Hands the freshly upserted alert off to the worker process via the {@code automation_outbox}
   * table. The worker polls {@code target_app='worker'} and dispatches by {@code job_name}.
   */
  private void dispatchToWorker(
      ZabbixWebhookIngestResponse response, ZabbixWebhookAlertMapping mapping) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("alertId", response.alertId());
    payload.put("datasourceId", response.datasourceId());
    payload.put("tenantId", response.tenantId());
    payload.put("sourceEventId", response.sourceEventId());
    payload.put("status", response.status());
    payload.put("created", response.created());
    payload.put("severity", mapping.severity());
    payload.put("assetExternalIds", mapping.hostIds());
    outboxWriter.enqueue(OUTBOX_TARGET_APP, OUTBOX_JOB_NAME, payload);
  }

  private ZabbixWebhookIngestResponse upsertAlert(
      DataSourceBinding datasource, ZabbixWebhookAlertMapping mapping, String assetId) {
    String labelsJson = writeJson(mapping.labels());
    String rawPayloadJson = writeJson(mapping.rawPayload());

    AlertUpsertResult result =
        jdbc.queryForObject(
            """
            insert into alert_event(id, tenant_id, source, source_event_id, severity, title, description,
                                    asset_id, entity_type, entity_name, labels, starts_at, ends_at, status,
                                    raw_payload, fingerprint, aggregation_key, created_at, updated_at)
            values (?, ?, 'zabbix', ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?::jsonb, ?, ?, now(), now())
            on conflict (tenant_id, source, source_event_id) where source_event_id is not null
            do update set
              severity = excluded.severity,
              title = excluded.title,
              description = excluded.description,
              asset_id = excluded.asset_id,
              entity_type = excluded.entity_type,
              entity_name = excluded.entity_name,
              labels = excluded.labels,
              starts_at = least(alert_event.starts_at, excluded.starts_at),
              ends_at = excluded.ends_at,
              status = excluded.status,
              raw_payload = excluded.raw_payload,
              fingerprint = excluded.fingerprint,
              aggregation_key = excluded.aggregation_key,
              updated_at = now()
            returning id, (xmax = 0) as created
            """,
            (rs, rowNum) -> new AlertUpsertResult(rs.getString("id"), rs.getBoolean("created")),
            newId("alert"),
            datasource.tenantId(),
            mapping.sourceEventId(),
            mapping.severity(),
            mapping.title(),
            mapping.description(),
            assetId,
            mapping.entityType(),
            mapping.entityName(),
            labelsJson,
            mapping.startsAt(),
            mapping.endsAt(),
            mapping.status(),
            rawPayloadJson,
            mapping.fingerprint(),
            mapping.aggregationKey());

    ZabbixWebhookIngestResponse response =
        new ZabbixWebhookIngestResponse(
            result.alertId(),
            datasource.id(),
            datasource.tenantId(),
            mapping.sourceEventId(),
            mapping.status(),
            result.created(),
            result.created() ? "Zabbix alert event created" : "Zabbix alert event updated");
    dispatchToWorker(response, mapping);
    return response;
  }

  private DataSourceBinding getDatasourceBinding(String datasourceId) {
    try {
      return jdbc.queryForObject(
          """
          select id, tenant_id
          from datasource
          where id = ? and type = 'zabbix'
          """,
          (rs, rowNum) -> new DataSourceBinding(rs.getString("id"), rs.getString("tenant_id")),
          datasourceId);
    } catch (EmptyResultDataAccessException ex) {
      throw new AppException("ZABBIX_DATASOURCE_NOT_FOUND", "Zabbix datasource not found");
    }
  }

  private String resolveAssetId(String tenantId, ZabbixWebhookAlertMapping mapping) {
    if (mapping.hostIds().isEmpty()) {
      return null;
    }
    String sourceId = ZabbixExternalIds.sourceId(mapping.datasourceId(), mapping.hostIds().get(0));
    try {
      return jdbc.queryForObject(
          """
          select id
          from asset
          where tenant_id = ? and source = 'zabbix' and source_id = ?
          """,
          String.class,
          tenantId,
          sourceId);
    } catch (EmptyResultDataAccessException ex) {
      return null;
    }
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new AppException("JSON_SERIALIZE_FAILED", "Failed to serialize JSON");
    }
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }

  private record DataSourceBinding(String id, String tenantId) {}

  private record AlertUpsertResult(String alertId, boolean created) {}
}
