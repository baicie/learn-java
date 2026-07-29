package io.aegisops.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OutboxReplayMigrationContractTest {
  private static final Path V0048 =
      Path.of("src/main/resources/db/migration/V0048__init_outbox_replay_intent.sql");

  @Test
  void addsDurableReplayIntentWithRollingUpgradeDefault() throws Exception {
    assertThat(V0048).exists();
    String sql = Files.readString(V0048).toLowerCase();

    assertThat(sql)
        .contains("alter table automation_outbox")
        .contains("replay_requested boolean not null default false");
  }
}
