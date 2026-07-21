package io.aegisops.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkRecordAiTraceMigrationContractTest {
  @Test
  void migrationAddsAuditableProviderTraceColumns() throws Exception {
    Path migration =
        Path.of(
            "src/main/resources/db/migration/V0044__init_work_record_ai_generation_trace.sql");

    assertThat(migration).exists();
    String sql = Files.readString(migration).toLowerCase();
    assertThat(sql)
        .contains("provider_run_id")
        .contains("provider_workflow_id")
        .contains("provider_workflow_version")
        .contains("provider_duration_ms")
        .contains("provider_total_tokens")
        .contains("warnings_json")
        .contains("fallback_reason")
        .contains("jsonb_typeof(warnings_json) = 'array'");
  }
}
