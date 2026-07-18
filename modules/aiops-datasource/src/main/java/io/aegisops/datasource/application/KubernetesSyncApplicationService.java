package io.aegisops.datasource.application;

import io.aegisops.asset.api.dto.AssetUpsertCommand;
import io.aegisops.asset.application.AssetApplicationService;
import io.aegisops.asset.domain.model.AssetIdentityInput;
import io.aegisops.common.exception.AppException;
import io.aegisops.datasource.application.port.DataSourceSyncStore;
import io.aegisops.kubernetes.application.KubernetesInventoryClientFactory;
import io.aegisops.kubernetes.domain.model.KubernetesResource;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class KubernetesSyncApplicationService {
  private final DataSourceSyncStore syncStore;
  private final KubernetesInventoryClientFactory clientFactory;
  private final AssetApplicationService assetService;

  public KubernetesSyncApplicationService(
      DataSourceSyncStore syncStore,
      KubernetesInventoryClientFactory clientFactory,
      AssetApplicationService assetService) {
    this.syncStore = syncStore;
    this.clientFactory = clientFactory;
    this.assetService = assetService;
  }

  public void execute(String tenantId, String datasourceId, String runId) {
    OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC);
    if (!syncStore.isPending(tenantId, datasourceId, runId)) {
      throw new AppException("DATASOURCE_SYNC_RUN_NOT_FOUND", "Pending sync run not found");
    }
    syncStore.start(tenantId, datasourceId, runId);
    Map<String, Object> stats = new LinkedHashMap<>();
    try {
      List<KubernetesResource> resources =
          clientFactory
              .create(syncStore.loadKubernetesConfig(tenantId, datasourceId))
              .listInventory();
      Map<String, String> assetIdsByUid = new LinkedHashMap<>();
      var cluster = assetService.upsert(clusterCommand(tenantId, datasourceId));
      String clusterAssetId = cluster.assetId();
      int created = "created".equals(cluster.action()) ? 1 : 0;
      int updated = 0;
      for (KubernetesResource resource : resources) {
        var result = assetService.upsert(command(tenantId, datasourceId, resource));
        assetIdsByUid.put(resource.uid(), result.assetId());
        if ("created".equals(result.action())) created++;
        else updated++;
      }
      for (KubernetesResource resource : resources) {
        String childId = assetIdsByUid.get(resource.uid());
        boolean hasKnownOwner = false;
        for (String ownerUid : resource.ownerUids()) {
          String ownerId = assetIdsByUid.get(ownerUid);
          if (ownerId != null) {
            hasKnownOwner = true;
            assetService.upsertSourceRelation(tenantId, ownerId, childId, "contains", "kubernetes");
          }
        }
        if (!hasKnownOwner) {
          assetService.upsertSourceRelation(
              tenantId, clusterAssetId, childId, "contains", "kubernetes");
        }
      }
      int missing = assetService.markMissing(tenantId, "kubernetes", datasourceId, startedAt);
      stats.put("resourcesCreated", created);
      stats.put("resourcesUpdated", updated);
      stats.put("resourcesMissing", missing);
      syncStore.complete(tenantId, datasourceId, runId, stats);
    } catch (RuntimeException exception) {
      syncStore.fail(tenantId, datasourceId, runId, stats, exception.getMessage());
      throw new AppException("DATASOURCE_SYNC_FAILED", exception.getMessage());
    }
  }

  private AssetUpsertCommand clusterCommand(String tenantId, String datasourceId) {
    return new AssetUpsertCommand(
        tenantId,
        "k8s_cluster",
        datasourceId,
        datasourceId,
        null,
        null,
        null,
        null,
        "normal",
        null,
        Map.of("k8s_kind", "Cluster"),
        "kubernetes",
        datasourceId,
        datasourceId,
        "cluster",
        "sync",
        Map.of(),
        List.of(new AssetIdentityInput("k8s_uid", datasourceId, "cluster", true)));
  }

  AssetUpsertCommand command(String tenantId, String datasourceId, KubernetesResource resource) {
    Map<String, Object> tags = new LinkedHashMap<>(resource.labels());
    if (resource.namespace() != null) tags.put("k8s_namespace", resource.namespace());
    tags.put("k8s_kind", resource.kind());
    return new AssetUpsertCommand(
        tenantId,
        assetType(resource.kind()),
        resource.name(),
        resource.name(),
        null,
        stringTag(resource.labels(), "environment"),
        null,
        null,
        "normal",
        resource.ip(),
        tags,
        "kubernetes",
        datasourceId,
        datasourceId,
        resource.uid(),
        "sync",
        resource.rawPayload(),
        identities(datasourceId, resource));
  }

  private List<AssetIdentityInput> identities(String datasourceId, KubernetesResource resource) {
    List<AssetIdentityInput> identities = new ArrayList<>();
    identities.add(new AssetIdentityInput("k8s_uid", datasourceId, resource.uid(), true));
    if (resource.machineId() != null) {
      identities.add(new AssetIdentityInput("machine_id", "global", resource.machineId(), true));
    }
    if (resource.providerId() != null) {
      identities.add(
          new AssetIdentityInput("cloud_instance_id", "global", resource.providerId(), true));
    }
    return List.copyOf(identities);
  }

  private String assetType(String kind) {
    return switch (kind) {
      case "Node" -> "k8s_node";
      case "Namespace" -> "k8s_namespace";
      case "Pod" -> "k8s_pod";
      case "Service" -> "service";
      case "Ingress" -> "endpoint";
      default -> "k8s_workload";
    };
  }

  private String stringTag(Map<String, Object> tags, String key) {
    Object value = tags.get(key);
    return value == null ? null : value.toString();
  }
}
