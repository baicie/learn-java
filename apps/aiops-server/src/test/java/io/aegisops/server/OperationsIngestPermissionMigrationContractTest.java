package io.aegisops.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OperationsIngestPermissionMigrationContractTest {
  private static final Path V0041 =
      Path.of("src/main/resources/db/migration/V0041__init_operations_ingest_permission.sql");

  @Test
  void shouldRegisterAiOpsPermissionsAndGrantThemToEverySystemAdmin() throws Exception {
    assertThat(V0041).exists();
    String sql = Files.readString(V0041);

    assertThat(sql)
        .contains(
            "'datasource:read'",
            "'datasource:write'",
            "'datasource:ingest'",
            "'asset:read'",
            "'asset:write'",
            "'asset:import'",
            "'alert:read'",
            "'alert:write'",
            "'incident:read'",
            "'incident:write'",
            "'incident:diagnose'",
            "'runbook:read'",
            "'runbook:write'",
            "'automation:read'",
            "'automation:approve'",
            "'automation:execute'",
            "'audit:read'",
            "'admin:manage'");
    assertThat(sql).contains("insert into iam.permission_definition");
    assertThat(sql).contains("insert into iam.permission(");
    assertThat(sql)
        .contains("insert into iam.role_permission(tenant_id,role_code,permission_code)");
    assertThat(sql).contains("r.role_code='system_admin'");
  }
}
