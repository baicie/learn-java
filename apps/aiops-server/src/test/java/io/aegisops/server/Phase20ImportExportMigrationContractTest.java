package io.aegisops.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Phase20ImportExportMigrationContractTest {
  @Test
  void migrationDefinesTenantScopedSingleUseUploadSessionsAndPermissions() throws Exception {
    Path migration =
        Path.of("src/main/resources/db/migration/V0031__init_phase20_import_export.sql");

    assertThat(migration).exists();
    String sql = Files.readString(migration).toLowerCase();
    assertThat(sql)
        .contains("create table if not exists work_record.wr_upload_session")
        .contains("foreign key (tenant_id)")
        .contains("unique (tenant_id, id)")
        .contains("status in ('prepared', 'consumed', 'expired')")
        .contains("work-record:import")
        .contains("work-record:export:async");
  }
}
