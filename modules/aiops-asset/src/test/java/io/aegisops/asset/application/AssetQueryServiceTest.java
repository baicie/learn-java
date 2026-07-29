package io.aegisops.asset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.asset.application.port.AssetStore;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AssetQueryServiceTest {

  @Test
  void shouldNormalizeSourceLinkCoordinatesBeforeLookup() {
    AssetStore repository = mock(AssetStore.class);
    AssetQueryService service = new AssetQueryService(repository);
    when(repository.findAssetIdBySourceLink("tenant-1", "zabbix", "ds-1", "10084"))
        .thenReturn(Optional.of("asset-1"));

    Optional<String> result =
        service.findAssetIdBySourceLink(" tenant-1 ", " ZABBIX ", " ds-1 ", " 10084 ");

    assertThat(result).contains("asset-1");
    verify(repository).findAssetIdBySourceLink("tenant-1", "zabbix", "ds-1", "10084");
  }
}
