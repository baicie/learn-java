package io.aegisops.datasource.application;

import io.aegisops.alert.AlertIngestRequest;
import io.aegisops.alert.AlertIngestService;
import io.aegisops.asset.api.dto.AssetUpsertCommand;
import io.aegisops.asset.application.AssetApplicationService;
import io.aegisops.asset.domain.model.AssetIdentityInput;
import io.aegisops.common.exception.AppException;
import io.aegisops.datasource.application.port.DataSourceSyncStore;
import io.aegisops.datasource.zabbix.ZabbixAlertEventMapping;
import io.aegisops.datasource.zabbix.ZabbixHostAssetMapping;
import io.aegisops.datasource.zabbix.ZabbixSyncMapper;
import io.aegisops.zabbix.ZabbixClient;
import io.aegisops.zabbix.ZabbixClientFactory;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DataSourceSyncApplicationService {
  private final DataSourceSyncStore syncStore;
  private final ZabbixClientFactory clientFactory;
  private final ZabbixSyncMapper mapper;
  private final AssetApplicationService assetService;
  private final AlertIngestService alertService;

  public DataSourceSyncApplicationService(
      DataSourceSyncStore syncStore,
      ZabbixClientFactory clientFactory,
      ZabbixSyncMapper mapper,
      AssetApplicationService assetService,
      AlertIngestService alertService) {
    this.syncStore = syncStore;
    this.clientFactory = clientFactory;
    this.mapper = mapper;
    this.assetService = assetService;
    this.alertService = alertService;
  }

  public void execute(String tenantId, String datasourceId, String runId) {
    OffsetDateTime syncStarted = OffsetDateTime.now(ZoneOffset.UTC);
    requirePendingRun(tenantId, datasourceId, runId);
    syncStore.start(tenantId, datasourceId, runId);
    SyncStats stats = new SyncStats();
    try {
      ZabbixClient client =
          clientFactory.create(syncStore.loadZabbixConfig(tenantId, datasourceId));
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
                  identities(mapping)));
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
    if (!syncStore.isPending(tenantId, datasourceId, runId)) {
      throw new AppException("DATASOURCE_SYNC_RUN_NOT_FOUND", "Pending sync run not found");
    }
  }

  private void complete(String tenantId, String datasourceId, String runId, SyncStats stats) {
    syncStore.complete(tenantId, datasourceId, runId, stats.toMap());
  }

  private void fail(
      String tenantId,
      String datasourceId,
      String runId,
      SyncStats stats,
      RuntimeException exception) {
    syncStore.fail(tenantId, datasourceId, runId, stats.toMap(), exception.getMessage());
  }

  private String stringTag(Map<String, Object> tags, String key) {
    Object value = tags.get(key);
    return value == null ? null : value.toString();
  }

  private List<AssetIdentityInput> identities(ZabbixHostAssetMapping mapping) {
    if (mapping.machineId() == null || mapping.machineId().isBlank()) {
      return List.of();
    }
    return List.of(new AssetIdentityInput("machine_id", "global", mapping.machineId(), true));
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
