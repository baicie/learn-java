package io.aegisops.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkRecordWeeklyAiMigrationContractTest {
  @Test
  void migrationAllowsWeeklyGenerationAndTenantWeekResources() throws Exception {
    Path migration =
        Path.of("src/main/resources/db/migration/V0052__init_work_record_weekly_ai_generation.sql");

    assertThat(migration).exists();
    String sql = Files.readString(migration).toLowerCase().replaceAll("\\s+", " ");
    assertThat(sql)
        .contains("'weekly_report'")
        .contains("'tenant_week'")
        .contains("ck_wr_ai_generation_period")
        .contains(
            "generation_type = 'weekly_report' and resource_type = 'tenant_week' and period_start is not null and period_end is not null and period_end = period_start + 6")
        .contains("生成记录摘要、周报和月报草稿");
  }
}
