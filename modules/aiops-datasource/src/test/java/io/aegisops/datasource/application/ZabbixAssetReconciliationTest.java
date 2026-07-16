package io.aegisops.datasource.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.alert.AlertIngestService;
import io.aegisops.asset.api.dto.AssetUpsertCommand;
import io.aegisops.asset.api.dto.AssetUpsertResult;
import io.aegisops.asset.application.AssetApplicationService;
import io.aegisops.datasource.zabbix.ZabbixSyncMapper;
import io.aegisops.zabbix.ZabbixClient;
import io.aegisops.zabbix.ZabbixClientFactory;
import io.aegisops.zabbix.ZabbixConfig;
import io.aegisops.zabbix.ZabbixHost;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class ZabbixAssetReconciliationTest {
  @Test
  void mapsHostIntoUnifiedAssetUpsertScopedByDatasourceInstance() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ZabbixClientFactory factory = mock(ZabbixClientFactory.class);
    ZabbixClient client = mock(ZabbixClient.class);
    AssetApplicationService assets = mock(AssetApplicationService.class);
    when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any(), any()))
        .thenReturn(1);
    when(jdbc.queryForObject(anyString(), eq(String.class), any(), any()))
        .thenReturn(
            "{\"endpoint\":\"https://zabbix.example/api_jsonrpc.php\",\"apiToken\":\"secret\"}");
    when(factory.create(any(ZabbixConfig.class))).thenReturn(client);
    when(client.getHosts(1000))
        .thenReturn(
            List.of(
                new ZabbixHost(
                    "10084", "db-prod-1", "DB Prod 1", "0", "10.0.0.8", List.of(), null)));
    when(client.getProblems(1000)).thenReturn(List.of());
    when(assets.upsert(any()))
        .thenReturn(new AssetUpsertResult("asset-1", "source-1", "created", false));
    when(assets.markMissing(eq("tenant-1"), eq("zabbix"), eq("ds-1"), any()))
        .thenReturn(0);
    var service =
        new DataSourceSyncApplicationService(
            jdbc,
            new ObjectMapper(),
            factory,
            new ZabbixSyncMapper(),
            assets,
            mock(AlertIngestService.class));

    service.execute("tenant-1", "ds-1", "sync-1");

    ArgumentCaptor<AssetUpsertCommand> command = ArgumentCaptor.forClass(AssetUpsertCommand.class);
    verify(assets).upsert(command.capture());
    assertThat(command.getValue().sourceType()).isEqualTo("zabbix");
    assertThat(command.getValue().sourceInstanceId()).isEqualTo("ds-1");
    assertThat(command.getValue().externalId()).isEqualTo("10084");
    assertThat(command.getValue().identities().getFirst().scopeKey()).isEqualTo("ds-1");
  }
}
