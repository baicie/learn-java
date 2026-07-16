package io.aegisops.asset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.asset.api.dto.AssetUpsertCommand;
import io.aegisops.asset.domain.model.AssetIdentityInput;
import io.aegisops.asset.domain.rule.AssetIdentityNormalizer;
import io.aegisops.asset.domain.rule.AssetIdentityResolver;
import io.aegisops.asset.infrastructure.persistence.AssetRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssetApplicationServiceTest {

  private AssetRepository repository;
  private AssetApplicationService service;

  @BeforeEach
  void setUp() {
    repository = mock(AssetRepository.class);
    service =
        new AssetApplicationService(
            repository, new AssetIdentityNormalizer(), new AssetIdentityResolver());
    when(repository.upsertSourceLink(any(), any(), any(OffsetDateTime.class)))
        .thenReturn("source-link-1");
  }

  @Test
  void exactSourceLinkMustReuseCanonicalAsset() {
    AssetUpsertCommand command = command("zabbix", "ds-1", "host-10084", "machine-1");
    when(repository.findAssetIdBySourceLink("tenant-1", "zabbix", "ds-1", "host-10084"))
        .thenReturn(Optional.of("asset-1"));
    when(repository.findAssetIdsByStrongIdentities(eq("tenant-1"), anyList()))
        .thenReturn(Set.of("asset-1"));

    var result = service.upsert(command);

    assertThat(result.assetId()).isEqualTo("asset-1");
    assertThat(result.action()).isEqualTo("updated");
    verify(repository).updateAsset(eq("asset-1"), eq(command), any(OffsetDateTime.class));
    verify(repository, never()).createAsset(any(), any());
  }

  @Test
  void sameStrongIdentityAcrossSourcesMustLinkOneAsset() {
    AssetUpsertCommand command = command("zabbix", "ds-1", "host-10084", "machine-1");
    when(repository.findAssetIdBySourceLink("tenant-1", "zabbix", "ds-1", "host-10084"))
        .thenReturn(Optional.empty());
    when(repository.findAssetIdsByStrongIdentities(eq("tenant-1"), anyList()))
        .thenReturn(Set.of("asset-csv"));

    var result = service.upsert(command);

    assertThat(result.assetId()).isEqualTo("asset-csv");
    assertThat(result.action()).isEqualTo("linked");
    verify(repository).updateAsset(eq("asset-csv"), eq(command), any(OffsetDateTime.class));
    verify(repository, never()).createAsset(any(), any());
  }

  @Test
  void ipOnlyMatchMustNotAutoMerge() {
    AssetUpsertCommand command = commandWithOnlyIp("csv", "sheet-a", "host-b", "10.0.0.8");
    when(repository.findAssetIdBySourceLink("tenant-1", "csv", "sheet-a", "host-b"))
        .thenReturn(Optional.empty());
    when(repository.findAssetIdsByStrongIdentities(eq("tenant-1"), anyList())).thenReturn(Set.of());
    when(repository.hasWeakIdentityConflict(eq("tenant-1"), anyList(), eq(null))).thenReturn(true);
    when(repository.createAsset(eq(command), any(OffsetDateTime.class))).thenReturn("asset-new");

    var result = service.upsert(command);

    assertThat(result.assetId()).isEqualTo("asset-new");
    assertThat(result.action()).isEqualTo("created");
    assertThat(result.hasWeakIdentityConflict()).isTrue();
  }

  private AssetUpsertCommand command(
      String sourceType, String sourceInstanceId, String externalId, String machineId) {
    return new AssetUpsertCommand(
        "tenant-1",
        "host",
        "host-a",
        "Host A",
        null,
        "production",
        null,
        null,
        "normal",
        "10.0.0.8",
        Map.of(),
        sourceType,
        sourceInstanceId,
        sourceType.equals("zabbix") ? sourceInstanceId : null,
        externalId,
        sourceType.equals("csv") ? "csv" : "sync",
        Map.of(),
        List.of(new AssetIdentityInput("machine_id", "global", machineId, true)));
  }

  private AssetUpsertCommand commandWithOnlyIp(
      String sourceType, String sourceInstanceId, String externalId, String ip) {
    return new AssetUpsertCommand(
        "tenant-1",
        "host",
        externalId,
        externalId,
        null,
        "production",
        null,
        null,
        "normal",
        ip,
        Map.of(),
        sourceType,
        sourceInstanceId,
        null,
        externalId,
        sourceType,
        Map.of(),
        List.of(new AssetIdentityInput("ip", "global", ip, false)));
  }
}
