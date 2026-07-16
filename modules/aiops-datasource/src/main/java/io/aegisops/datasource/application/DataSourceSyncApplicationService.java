package io.aegisops.datasource.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.alert.AlertIngestRequest;
import io.aegisops.alert.AlertIngestService;
import io.aegisops.asset.api.dto.AssetUpsertCommand;
import io.aegisops.asset.application.AssetApplicationService;
import io.aegisops.asset.domain.model.AssetIdentityInput;
import io.aegisops.common.exception.AppException;
import io.aegisops.datasource.zabbix.ZabbixAlertEventMapping;
import io.aegisops.datasource.zabbix.ZabbixHostAssetMapping;
import io.aegisops.datasource.zabbix.ZabbixSyncMapper;
import io.aegisops.zabbix.ZabbixClient;
import io.aegisops.zabbix.ZabbixClientFactory;
import io.aegisops.zabbix.ZabbixConfig;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DataSourceSyncApplicationService {
  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final ZabbixClientFactory clientFactory;
  private final ZabbixSyncMapper mapper;
  private final AssetApplicationService assetService;
  private final AlertIngestService alertService;

  public DataSourceSyncApplicationService(
      JdbcTemplate jdbc,
      ObjectMapper objectMapper,
      ZabbixClientFactory clientFactory,
      ZabbixSyncMapper mapper,
      AssetApplicationService assetService,
      AlertIngestService alertService) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.clientFactory = clientFactory;
    this.mapper = mapper;
    this.assetService = assetService;
    this.alertService = alertService;
  }

  public void execute(String tenantId, String datasourceId, String runId) {
    OffsetDateTime syncStarted = OffsetDateTime.now(ZoneOffset.UTC);
    requirePendingRun(tenantId, datasourceId, runId);
    jdbc.update(
        "update datasource_sync_run set status='running' where tenant_id=? and datasource_id=? and id=?",
        tenantId,
        datasourceId,
        runId);
    SyncStats stats = new SyncStats();
    try {
      ZabbixClient client = clientFactory.create(loadConfig(tenantId, datasourceId));
      Map<String, String> assetIdsByHostId = syncHosts(tenantId, datasourceId, client, stats);
      stats.hostsMissing = assetService.markMissing(tenantId, "zabbix", datasourceId, syncStarted);
      syncProblems(tenantId, datasourceId, client, assetIdsByHostId, stats);
      complete(tenantId, datasourceId, runId, stats);
    } catch (RuntimeException exception) {
      fail(tenantId, datasourceId, runId, stats, exception);
      throw new AppException("DATASOURCE_SYNC_FAILED", exception.getMessage());
    }
  }

  private Map<String, String> syncHosts(
      String tenantId, String datasourceId, ZabbixClient client, SyncStats stats) {
    Map<String, String> assetIdsByHostId = new LinkedHashMap<>();
    for (var host : client.getHosts(1000)) {
      ZabbixHostAssetMapping mapping = mapper.mapHost(datasourceId, host);
      if (mapping == null) {
        continue;
      }
      var result =
          assetService.upsert(
              new AssetUpsertCommand(
                  tenantId,
                  "host",
                  mapping.name(),
                  mapping.displayName(),
                  null,
                  stringTag(mapping.tags(), "environment"),
                  null,
                  null,
                  "normal",
                  mapping.ip(),
                  mapping.tags(),
                  "zabbix",
                  datasourceId,
                  datasourceId,
                  host.hostId(),
                  "sync",
                  Map.of("hostId", host.hostId(), "status", mapping.status()),
                  List.of(
                      new AssetIdentityInput(
                          "zabbix_host_id", datasourceId, host.hostId(), true))));
      assetIdsByHostId.put(host.hostId(), result.assetId());
      if (result.action().equals("created")) {
        stats.hostsCreated++;
      } else {
        stats.hostsUpdated++;
      }
    }
    return assetIdsByHostId;
  }

  private void syncProblems(
      String tenantId,
      String datasourceId,
      ZabbixClient client,
      Map<String, String> assetIdsByHostId,
      SyncStats stats) {
    for (var problem : client.getProblems(1000)) {
      ZabbixAlertEventMapping mapping = mapper.mapProblem(datasourceId, problem);
      if (mapping == null) {
        continue;
      }
      String assetId =
          mapping.hostIds().isEmpty() ? null : assetIdsByHostId.get(mapping.hostIds().getFirst());
      var result =
          alertService.ingest(
              tenantId,
              new AlertIngestRequest(
                  "zabbix",
                  mapping.sourceEventId(),
                  mapping.severity(),
                  mapping.title(),
                  mapping.description(),
                  assetId,
                  mapping.entityType(),
                  mapping.entityName(),
                  mapping.labels(),
                  mapping.startsAt(),
                  null,
                  mapping.status(),
                  rawPayload(mapping.rawPayload())));
      if (result.created()) {
        stats.alertsCreated++;
      } else {
        stats.alertsUpdated++;
      }
    }
  }

  private void requirePendingRun(String tenantId, String datasourceId, String runId) {
    Integer count =
        jdbc.queryForObject(
            "select count(*) from datasource_sync_run where tenant_id=? and datasource_id=? and id=? and status='pending'",
            Integer.class,
            tenantId,
            datasourceId,
            runId);
    if (count == null || count != 1) {
      throw new AppException("DATASOURCE_SYNC_RUN_NOT_FOUND", "Pending sync run not found");
    }
  }

  private ZabbixConfig loadConfig(String tenantId, String datasourceId) {
    try {
      String json =
          jdbc.queryForObject(
              "select config_json::text from datasource where tenant_id=? and id=? and type='zabbix'",
              String.class,
              tenantId,
              datasourceId);
      return objectMapper.readValue(json, ZabbixConfig.class);
    } catch (EmptyResultDataAccessException exception) {
      throw new AppException("DATASOURCE_NOT_FOUND", "Datasource not found");
    } catch (JsonProcessingException exception) {
      throw new AppException("DATASOURCE_CONFIG_INVALID", "Datasource config is invalid");
    }
  }

  private void complete(String tenantId, String datasourceId, String runId, SyncStats stats) {
    jdbc.update(
        "update datasource_sync_run set status='success',message='Sync completed',stats_json=?::jsonb,finished_at=now() where tenant_id=? and id=?",
        json(stats.toMap()),
        tenantId,
        runId);
    jdbc.update(
        "update datasource set status='active',last_sync_at=now(),updated_at=now() where tenant_id=? and id=?",
        tenantId,
        datasourceId);
  }

  private void fail(
      String tenantId,
      String datasourceId,
      String runId,
      SyncStats stats,
      RuntimeException exception) {
    jdbc.update(
        "update datasource_sync_run set status='failed',message=?,stats_json=?::jsonb,finished_at=now() where tenant_id=? and id=?",
        exception.getMessage(),
        json(stats.toMap()),
        tenantId,
        runId);
    jdbc.update(
        "update datasource set status='error',updated_at=now() where tenant_id=? and id=?",
        tenantId,
        datasourceId);
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("sync stats cannot be serialized", exception);
    }
  }

  private String stringTag(Map<String, Object> tags, String key) {
    Object value = tags.get(key);
    return value == null ? null : value.toString();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> rawPayload(Object value) {
    if (value instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    return Map.of("value", value == null ? "" : value.toString());
  }

  private static final class SyncStats {
    int hostsCreated;
    int hostsUpdated;
    int hostsMissing;
    int alertsCreated;
    int alertsUpdated;

    Map<String, Object> toMap() {
      return Map.of(
          "hostsCreated", hostsCreated,
          "hostsUpdated", hostsUpdated,
          "hostsMissing", hostsMissing,
          "alertsCreated", alertsCreated,
          "alertsUpdated", alertsUpdated);
    }
  }
}
