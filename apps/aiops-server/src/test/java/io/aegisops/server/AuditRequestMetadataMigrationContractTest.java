package io.aegisops.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuditRequestMetadataMigrationContractTest {
  private static final Path V0049 =
      Path.of("src/main/resources/db/migration/V0049__init_audit_request_metadata.sql");

  @Test
  void addsRequestMetadataToAuditLog() throws Exception {
    assertThat(V0049).exists();
    String sql = Files.readString(V0049).toLowerCase();

    assertThat(sql)
        .contains("alter table public.audit_log")
        .contains("add column if not exists request_id")
        .contains("add column if not exists ip")
        .contains("add column if not exists user_agent");
  }
}
