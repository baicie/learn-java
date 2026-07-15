package io.aegisops.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Phase20AsyncFoundationMigrationContractTest {

  private static final Path V0030 =
      Path.of("src/main/resources/db/migration/V0030__init_phase20_async_foundation.sql");

  @Test
  void shouldCreateLeaseSafeOutboxAndTenantScopedAsyncJobs() throws Exception {
    assertThat(V0030).exists();
    String sql = Files.readString(V0030);

    assertThat(sql).contains("available_at timestamptz");
    assertThat(sql).contains("lease_until timestamptz");
    assertThat(sql).contains("idempotency_key varchar(128)");
    assertThat(sql).contains("create table if not exists work_record.wr_async_job");
    assertThat(sql).contains("create table if not exists work_record.wr_async_job_item");
    assertThat(sql).contains("tenant_id varchar(64) not null");
    assertThat(sql).contains("references public.tenant(id)");
    assertThat(sql).contains("uq_wr_async_job_idempotency");
    assertThat(sql).contains("foreign key (tenant_id, job_id)");
    assertThat(sql).contains("idx_automation_outbox_claim");
    assertThat(sql).contains("idx_automation_outbox_lease");
  }
}
