package io.aegisops.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AsyncExportRolePermissionMigrationContractTest {

  @Test
  void normalUserDoesNotKeepAsyncExportWithoutItsExportDependency() throws Exception {
    Path migration =
        Path.of("src/main/resources/db/migration/V0042__init_async_job_permissions.sql");

    assertThat(Files.readString(migration))
        .contains("role_code = 'normal_user'")
        .contains("permission_code = 'work-record:export:async'");
  }
}
