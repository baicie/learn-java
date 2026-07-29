package io.aegisops.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DatasourceSyncRunLeaseMigrationContractTest {
  private static final Path V0047 =
      Path.of("src/main/resources/db/migration/V0047__init_datasource_sync_run_lease.sql");

  @Test
  void addsLeaseAndFencingTokenForRetryableDatasourceRuns() throws Exception {
    assertThat(V0047).exists();
    String sql = Files.readString(V0047).toLowerCase();

    assertThat(sql)
        .contains("add column if not exists lease_until timestamptz")
        .contains("add column if not exists claim_token varchar(64)")
        .contains("add column if not exists datasource_updated_at timestamptz")
        .contains("where status = 'running'")
        .contains("idx_datasource_sync_run_running_lease")
        .contains("idx_datasource_sync_run_active");
  }

  @Test
  void addsOwnerTokenForOutboxLeaseFencing() throws Exception {
    assertThat(V0047).exists();
    String sql = Files.readString(V0047).toLowerCase();

    assertThat(sql)
        .contains("alter table automation_outbox")
        .contains("add column if not exists claim_token varchar(64)");
  }
}
