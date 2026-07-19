package io.aegisops.server.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.asset.application.AssetImportService;
import io.aegisops.asset.application.AssetManagementService;
import io.aegisops.asset.application.AssetQuery;
import io.aegisops.asset.application.AssetQueryService;
import io.aegisops.datasource.application.DataSourceSyncApplicationService;
import io.aegisops.server.AiOpsServerApplication;
import io.aegisops.zabbix.ZabbixClient;
import io.aegisops.zabbix.ZabbixClientFactory;
import io.aegisops.zabbix.ZabbixHost;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = AiOpsServerApplication.class)
@ActiveProfiles("acceptance")
@Testcontainers
class AssetSourceFoundationAcceptanceIT {

  private static final String TENANT_A = "tenant_asset_acceptance_a";
  private static final String TENANT_B = "tenant_asset_acceptance_b";
  private static final String ACTOR = "asset-acceptance";

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private JdbcTemplate jdbc;
  @Autowired private AssetImportService importService;
  @Autowired private AssetQueryService queryService;
  @Autowired private AssetManagementService managementService;
  @Autowired private DataSourceSyncApplicationService syncService;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private ZabbixClientFactory clientFactory;

  @BeforeEach
  void setUp() {
    jdbc.update("delete from tenant where id in (?, ?)", TENANT_A, TENANT_B);
    jdbc.update(
        "insert into tenant(id,code,name,status) values (?,?,?,'active')",
        TENANT_A,
        "asset-acceptance-a",
        "资源验收租户 A");
    jdbc.update(
        "insert into tenant(id,code,name,status) values (?,?,?,'active')",
        TENANT_B,
        "asset-acceptance-b",
        "资源验收租户 B");
  }

  @Test
  void csvAndZabbixReconcileByMachineIdWithinTenantOnly() throws Exception {
    importCsv(TENANT_A, "csv-a", "csv-host-a", "10.0.0.8", "machine-001");
    assertThat(assetCount(TENANT_A)).isEqualTo(1);

    ZabbixClient client = mock(ZabbixClient.class);
    when(clientFactory.create(any())).thenReturn(client);
    when(client.getHosts(1000))
        .thenReturn(
            List.of(
                new ZabbixHost(
                    "10001",
                    "zabbix-host-a",
                    "Zabbix Host A",
                    "0",
                    "10.0.0.8",
                    List.of("Linux servers"),
                    "machine-001",
                    objectMapper.createObjectNode())));
    when(client.getProblems(1000)).thenReturn(List.of());

    jdbc.update(
        "insert into datasource(id,tenant_id,type,name,status,config_json) values (?,?, 'zabbix',?,'inactive',?::jsonb)",
        "ds-zabbix-a",
        TENANT_A,
        "验收 Zabbix",
        "{\"endpoint\":\"http://zabbix.invalid/api_jsonrpc.php\",\"apiToken\":\"test\"}");
    jdbc.update(
        "insert into datasource_sync_run(id,tenant_id,datasource_id,status,created_by) values (?,?,?,'pending',?)",
        "sync-zabbix-a",
        TENANT_A,
        "ds-zabbix-a",
        ACTOR);

    syncService.execute(TENANT_A, "ds-zabbix-a", "sync-zabbix-a");

    assertThat(assetCount(TENANT_A)).isEqualTo(1);
    String reconciledAssetId = onlyAssetId(TENANT_A);
    assertThat(queryService.sources(TENANT_A, reconciledAssetId))
        .extracting(source -> source.sourceType())
        .containsExactlyInAnyOrder("csv", "zabbix");

    importCsv(TENANT_B, "csv-b", "csv-host-b", "10.0.0.8", "machine-001");
    assertThat(assetCount(TENANT_B)).isEqualTo(1);
    assertThat(onlyAssetId(TENANT_B)).isNotEqualTo(reconciledAssetId);

    importCsv(TENANT_A, "csv-a", "csv-host-c", "10.0.0.8", "machine-002");
    assertThat(assetCount(TENANT_A)).isEqualTo(2);

    String secondAssetId =
        jdbc.queryForObject(
            "select id from asset where tenant_id=? and id<>? and deleted_at is null",
            String.class,
            TENANT_A,
            reconciledAssetId);
    var secondAsset = queryService.get(TENANT_A, secondAssetId);
    managementService.archive(TENANT_A, secondAssetId, secondAsset.version(), ACTOR);

    assertThat(assetCount(TENANT_A)).isEqualTo(1);
    assertThat(queryService.page(TENANT_A, new AssetQuery(1, 20, null, null, null, null)).items())
        .extracting(asset -> asset.id())
        .containsExactly(reconciledAssetId);
  }

  private void importCsv(
      String tenantId, String sourceInstanceId, String externalId, String ip, String machineId) {
    String csv =
        "external_id,asset_type,name,display_name,environment,site,owner_team,criticality,ip,machine_id,cloud_instance_id,k8s_uid,tags\n"
            + externalId
            + ",host,"
            + externalId
            + ","
            + externalId
            + ",production,shanghai,ops,normal,"
            + ip
            + ","
            + machineId
            + ",,,role=server\n";
    var preview =
        importService.preview(
            tenantId,
            sourceInstanceId,
            externalId + ".csv",
            csv.getBytes(StandardCharsets.UTF_8),
            ACTOR);
    assertThat(preview.invalidRows()).isZero();
    assertThat(preview.conflictRows()).isZero();
    assertThat(importService.confirm(tenantId, preview.jobId(), ACTOR).status())
        .isEqualTo("success");
  }

  private long assetCount(String tenantId) {
    return queryService.page(tenantId, new AssetQuery(1, 100, null, null, null, null)).total();
  }

  private String onlyAssetId(String tenantId) {
    return queryService
        .page(tenantId, new AssetQuery(1, 100, null, null, null, null))
        .items()
        .getFirst()
        .id();
  }
}
