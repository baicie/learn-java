package io.aegisops.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AssetSourceFoundationMigrationContractTest {

  private static final Path V0037 =
      Path.of("src/main/resources/db/migration/V0037__init_asset_source_foundation.sql");

  private static final Path JOOQ_CONFIG =
      Path.of("../../modules/aiops-persistence/src/main/resources/jooq-codegen.xml");

  @Test
  void shouldCreateTenantScopedAssetSourceIdentityAndImportTables() throws Exception {
    assertThat(V0037).exists();
    String sql = Files.readString(V0037);

    assertThat(sql).contains("create table asset_source_link");
    assertThat(sql).contains("create table asset_identity");
    assertThat(sql).contains("create table asset_import_job");
    assertThat(sql).contains("create table asset_import_row");
    assertThat(sql).contains("tenant_id varchar(64) not null");
    assertThat(sql).contains("uq_asset_source_link_external");
    assertThat(sql).contains("uq_asset_identity_strong");
  }

  @Test
  void shouldExposeNewAssetTablesToJooqCodeGeneration() throws Exception {
    assertThat(JOOQ_CONFIG).exists();
    String xml = Files.readString(JOOQ_CONFIG);

    assertThat(xml).contains("asset_source_link");
    assertThat(xml).contains("asset_identity");
    assertThat(xml).contains("asset_import_job");
    assertThat(xml).contains("asset_import_row");
  }
}
