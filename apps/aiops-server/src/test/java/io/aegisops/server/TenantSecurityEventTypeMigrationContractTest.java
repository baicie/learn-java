package io.aegisops.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TenantSecurityEventTypeMigrationContractTest {
  private static final Path V0050 =
      Path.of("src/main/resources/db/migration/V0050__init_internal_agent_security_events.sql");

  @Test
  void preservesEverySecurityEventTypeAndAddsInternalAgentFailures() throws Exception {
    assertThat(V0050).exists();
    String sql = Files.readString(V0050).toLowerCase();
    Pattern eventType = Pattern.compile("'([^']+)'");

    assertThat(sql)
        .contains("alter table tenant_security_event")
        .contains("drop constraint if exists ck_tenant_security_event_type");
    assertThat(eventType.matcher(sql).results().map(result -> result.group(1)))
        .containsExactlyInAnyOrder(
            "tenant_missing",
            "internal_auth_failed",
            "internal_auth_forbidden",
            "internal_auth_unavailable",
            "diagnosis_grant_invalid",
            "rate_limited",
            "quota_exceeded",
            "cross_tenant_denied",
            "internal_auth_succeeded");
  }
}
