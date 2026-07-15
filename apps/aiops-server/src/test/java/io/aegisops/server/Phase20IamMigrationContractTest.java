package io.aegisops.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Phase20IamMigrationContractTest {
  private static final Path V0029 =
      Path.of("src/main/resources/db/migration/V0029__init_harden_portal_iam.sql");

  @Test
  void shouldSeedBuiltInRolesForTenantsCreatedAfterMigration() throws Exception {
    String sql = Files.readString(V0029);

    assertThat(sql).contains("function iam.seed_tenant_builtin_roles()");
    assertThat(sql).contains("trg_seed_tenant_builtin_roles");
    assertThat(sql).contains("after insert on tenant");
    assertThat(sql).contains("new.id, r.role_code");
    assertThat(sql).contains("insert into iam.role_permission");
    assertThat(sql).contains("insert into iam.role_data_scope_v2");
  }
}
