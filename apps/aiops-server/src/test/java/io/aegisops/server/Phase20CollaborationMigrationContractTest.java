package io.aegisops.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Phase20CollaborationMigrationContractTest {
  @Test
  void collaborationTablesUseTenantScopedForeignKeysAndPermissions() throws Exception {
    String sql;
    try (var input =
        getClass().getResourceAsStream("/db/migration/V0032__init_phase20_collaboration.sql")) {
      assertThat(input).isNotNull();
      sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }

    assertThat(sql)
        .contains("foreign key (tenant_id, record_id)")
        .contains("foreign key (tenant_id, created_by)")
        .contains("'pending_scan', 'ready', 'quarantined', 'deleted'")
        .contains("work-record:comment:moderate")
        .contains("work-record:attachment:moderate")
        .contains("work-record:relation");
  }
}
