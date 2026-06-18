package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.execution.dto.WebhookConnectorCreateCommand;
import io.aegisops.execution.dto.WebhookPolicyCreateCommand;
import java.util.concurrent.atomic.AtomicReference;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.conf.StatementType;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class JooqWebhookRepositoryGeneratedSqlTest {
  @Test
  void createConnectorUsesWebhookConnectorTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqWebhookRepository repository =
        new JooqWebhookRepository(
            DSL.using(
                new MockConnection(provider),
                SQLDialect.POSTGRES,
                new Settings().withStatementType(StatementType.STATIC_STATEMENT)));

    repository.createConnector(
        new WebhookConnectorCreateCommand(
            "whc_1",
            "tenant_1",
            "ops",
            "desc",
            "https://ops.example.com",
            "POST",
            "{}",
            "[\"authorization\"]",
            true,
            "alice"));

    String sql = sqlRef.get();
    if (sql == null) {
      throw new AssertionError("SQL was not captured");
    }
    String lower = sql.toLowerCase();

    assertTrue(lower.contains("insert into"), "expected insert into, got: " + sql);
    assertTrue(lower.contains("webhook_connector"), "expected webhook_connector, got: " + sql);
    assertTrue(lower.contains("ops.example.com"), "expected base url in sql, got: " + sql);
  }

  @Test
  void createPolicyUsesWebhookExecutionPolicyTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqWebhookRepository repository =
        new JooqWebhookRepository(
            DSL.using(
                new MockConnection(provider),
                SQLDialect.POSTGRES,
                new Settings().withStatementType(StatementType.STATIC_STATEMENT)));

    repository.createPolicy(
        new WebhookPolicyCreateCommand(
            "whp_1",
            "tenant_1",
            "whc_1",
            false,
            "[\"ops.example.com\"]",
            "[\"POST\"]",
            true,
            true,
            true,
            32768,
            5000,
            true));

    String sql = sqlRef.get();
    if (sql == null) {
      throw new AssertionError("SQL was not captured");
    }
    String lower = sql.toLowerCase();

    assertTrue(lower.contains("insert into"), "expected insert into, got: " + sql);
    assertTrue(
        lower.contains("webhook_execution_policy"),
        "expected webhook_execution_policy, got: " + sql);
    assertTrue(lower.contains("ops.example.com"), "expected allowed host in sql, got: " + sql);
  }

  @Test
  void setConnectorEnabledUsesUpdateWebhookConnector() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqWebhookRepository repository =
        new JooqWebhookRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.setConnectorEnabled("tenant_1", "whc_1", false);

    String sql = sqlRef.get();
    if (sql == null) {
      throw new AssertionError("SQL was not captured");
    }
    String lower = sql.toLowerCase();

    assertTrue(lower.contains("update"), "expected update, got: " + sql);
    assertTrue(lower.contains("webhook_connector"), "expected webhook_connector, got: " + sql);
  }
}
