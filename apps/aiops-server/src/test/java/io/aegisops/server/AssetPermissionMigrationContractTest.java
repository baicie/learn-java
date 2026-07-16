package io.aegisops.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AssetPermissionMigrationContractTest {
  private static final Path V0038 =
      Path.of("src/main/resources/db/migration/V0038__init_asset_permissions.sql");

  @Test
  void shouldRegisterAssetPermissionsAndGrantThemToEverySystemAdmin() throws Exception {
    assertThat(V0038).exists();
    String sql = Files.readString(V0038);

    assertThat(sql).contains("'asset:read'");
    assertThat(sql).contains("'asset:write'");
    assertThat(sql).contains("'asset:import'");
    assertThat(sql).contains("insert into iam.permission_definition");
    assertThat(sql)
        .contains("insert into iam.role_permission(tenant_id,role_code,permission_code)");
    assertThat(sql).contains("r.role_code='system_admin'");
  }
}
