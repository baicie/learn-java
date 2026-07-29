package io.aegisops.datasource.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.alert.AlertIngestRequest;
import io.aegisops.asset.api.dto.AssetUpsertCommand;
import io.aegisops.asset.domain.model.AssetIdentityInput;
import io.aegisops.common.exception.AppException;
import io.aegisops.datasource.application.port.DataSourceSyncStore;
import io.aegisops.datasource.application.port.DataSourceSyncStore.SyncRunClaimResult;
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
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DataSourceSyncApplicationService {
  private final DataSourceSyncStore syncStore;
  private final ZabbixClientFactory clientFactory;
  private final ZabbixSyncMapper mapper;
  private final DataSourceSyncWriteService syncWriteService;
  private final ObjectMapper objectMapper;

  public DataSourceSyncApplicationService(
      DataSourceSyncStore syncStore,
      ZabbixClientFactory clientFactory,
      ZabbixSyncMapper mapper,
      DataSourceSyncWriteService syncWriteService,
      ObjectMapper objectMapper) {
    this.syncStore = syncStore;
    this.clientFactory = clientFactory;
    this.mapper = mapper;
    this.syncWriteService = syncWriteService;
    this.objectMapper = objectMapper;
  }

  public void execute(String tenantId, String datasourceId, String runId) {
    execute(tenantId, datasourceId, runId, UUID.randomUUID().toString());
  }

  public void execute(String tenantId, String datasourceId, String runId, String claimToken) {
    if (claimToken == null || claimToken.isBlank()) {
      throw new IllegalArgumentException("claimToken is required");
    }
    DataSourceSyncClaim syncClaim =
        new DataSourceSyncClaim(tenantId, datasourceId, runId, claimToken);
    OffsetDateTime syncStarted = OffsetDateTime.now(ZoneOffset.UTC);
    SyncRunClaimResult claimResult =
        syncStore.claimForExecution(tenantId, datasourceId, runId, claimToken);
    if (claimResult == SyncRunClaimResult.ALREADY_COMPLETED) {
      return;
    }
    if (claimResult == SyncRunClaimResult.ACTIVE) {
      throw new AppException(
          "DATASOURCE_SYNC_RUN_ACTIVE", "Datasource sync run is already running");
    }
    if (claimResult != SyncRunClaimResult.ACQUIRED) {
      throw new AppException("DATASOURCE_SYNC_RUN_NOT_FOUND", "Runnable sync run not found");
    }
    ensureClaimActive(syncClaim);
    SyncStats stats = new SyncStats();
    try {
      ZabbixClient client =
          clientFactory.create(syncStore.loadZabbixConfig(tenantId, datasourceId));
      Map<String, String> assetIdsByHostId = syncHosts(syncClaim, client, stats);
      ensureClaimActive(syncClaim);
      stats.hostsMissing = syncWriteService.markMissingHosts(syncClaim, syncStarted);
      syncProblems(syncClaim, client, assetIdsByHostId, stats);
    } catch (AppException exception) {
      if ("DATASOURCE_SYNC_CLAIM_LOST".equals(exception.errorCode())) {
        throw exception;
      }
      if (!fail(syncClaim, stats, exception)) {
        throw claimLost();
      }
      throw new AppException("DATASOURCE_SYNC_FAILED", exception.getMessage());
    } catch (RuntimeException exception) {
      if (!fail(syncClaim, stats, exception)) {
        throw claimLost();
      }
      throw new AppException("DATASOURCE_SYNC_FAILED", exception.getMessage());
    }
    ensureClaimActive(syncClaim);
    if (!complete(syncClaim, stats)) {
      throw claimLost();
    }
  }

  public boolean renewLease(
      String tenantId,
      String datasourceId,
      String runId,
      String claimToken,
      OffsetDateTime leaseUntil) {
    return syncStore.renewClaim(tenantId, datasourceId, runId, claimToken, leaseUntil);
  }

  private Map<String, String> syncHosts(
      DataSourceSyncClaim claim, ZabbixClient client, SyncStats stats) {
    Map<String, String> assetIdsByHostId = new LinkedHashMap<>();
    var hosts = client.getHosts(1000);
    ensureClaimActive(claim);
    for (var host : hosts) {
      ZabbixHostAssetMapping mapping = mapper.mapHost(claim.datasourceId(), host);
      if (mapping == null) {
        continue;
      }
      ensureClaimActive(claim);
      var result =
          syncWriteService.upsertHost(
              claim,
              host.hostId(),
              new AssetUpsertCommand(
                  claim.tenantId(),
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
                  claim.datasourceId(),
                  claim.datasourceId(),
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
    ensureClaimActive(claim);
    syncWriteService.reconcileIncidentAssets(claim);
    return assetIdsByHostId;
  }

  private void syncProblems(
      DataSourceSyncClaim claim,
      ZabbixClient client,
      Map<String, String> assetIdsByHostId,
      SyncStats stats) {
    var problems = client.getProblems(1000);
    ensureClaimActive(claim);
    for (var problem : problems) {
      ZabbixAlertEventMapping mapping = mapper.mapProblem(claim.datasourceId(), problem);
      if (mapping == null) {
        continue;
      }
      ensureClaimActive(claim);
      String assetId =
          mapping.hostIds().isEmpty() ? null : assetIdsByHostId.get(mapping.hostIds().getFirst());
      var result =
          syncWriteService.ingestAlert(
              claim,
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
                  mapping.endsAt(),
                  mapping.status(),
                  rawPayload(mapping.rawPayload())),
              mapping.fingerprint(),
              mapping.aggregationKey());
      if (result.created()) {
        stats.alertsCreated++;
      } else {
        stats.alertsUpdated++;
      }
    }
  }

  private boolean complete(DataSourceSyncClaim claim, SyncStats stats) {
    return syncStore.completeClaimed(
        claim.tenantId(), claim.datasourceId(), claim.runId(), claim.claimToken(), stats.toMap());
  }

  private boolean fail(DataSourceSyncClaim claim, SyncStats stats, RuntimeException exception) {
    return syncStore.failClaimed(
        claim.tenantId(),
        claim.datasourceId(),
        claim.runId(),
        claim.claimToken(),
        stats.toMap(),
        exception.getMessage());
  }

  private AppException claimLost() {
    return new AppException(
        "DATASOURCE_SYNC_CLAIM_LOST", "Datasource sync run was claimed by another worker");
  }

  private void ensureClaimActive(DataSourceSyncClaim claim) {
    OffsetDateTime leaseUntil = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5);
    if (!renewLease(
        claim.tenantId(), claim.datasourceId(), claim.runId(), claim.claimToken(), leaseUntil)) {
      throw claimLost();
    }
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
    if (value instanceof JsonNode node && node.isObject()) {
      return objectMapper.convertValue(node, new TypeReference<>() {});
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
