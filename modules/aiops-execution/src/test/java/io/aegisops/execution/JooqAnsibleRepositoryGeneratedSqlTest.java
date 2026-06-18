package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.execution.dto.AnsibleInventoryCreateCommand;
import io.aegisops.execution.dto.AnsiblePlaybookCreateCommand;
import io.aegisops.execution.dto.AnsiblePolicyCreateCommand;
import java.util.concurrent.atomic.AtomicReference;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.conf.StatementType;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class JooqAnsibleRepositoryGeneratedSqlTest {
  @Test
  void createInventoryUsesAnsibleInventoryTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqAnsibleRepository repository =
        new JooqAnsibleRepository(
            DSL.using(
                new MockConnection(provider),
                SQLDialect.POSTGRES,
                new Settings().withStatementType(StatementType.STATIC_STATEMENT)));

    repository.createInventory(
        new AnsibleInventoryCreateCommand(
            "inv_1", "tenant_1", "prod", "desc", "inline", "[web]\n10.0.0.1", null, true, "alice"));

    String sql = sqlRef.get();
    if (sql == null) {
      throw new AssertionError("SQL was not captured");
    }
    String lower = sql.toLowerCase();

    assertTrue(lower.contains("insert into"), "expected insert into, got: " + sql);
    assertTrue(lower.contains("ansible_inventory"), "expected ansible_inventory, got: " + sql);
    assertTrue(lower.contains("prod"), "expected inventory name in sql, got: " + sql);
  }

  @Test
  void createPlaybookUsesAnsiblePlaybookTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqAnsibleRepository repository =
        new JooqAnsibleRepository(
            DSL.using(
                new MockConnection(provider),
                SQLDialect.POSTGRES,
                new Settings().withStatementType(StatementType.STATIC_STATEMENT)));

    repository.createPlaybook(
        new AnsiblePlaybookCreateCommand(
            "pb_1",
            "tenant_1",
            "restart",
            "desc",
            "playbooks/restart.yml",
            null,
            "{}",
            "[\"restart\"]",
            true,
            "alice",
            true,
            true,
            true,
            true,
            "[\"inv_1\"]",
            "[\"service_name\"]",
            "[\"low\",\"medium\"]",
            "[]",
            true,
            32768,
            1800,
            3600));

    String sql = sqlRef.get();
    if (sql == null) {
      throw new AssertionError("SQL was not captured");
    }
    String lower = sql.toLowerCase();

    assertTrue(lower.contains("insert into"), "expected insert into, got: " + sql);
    assertTrue(lower.contains("ansible_playbook"), "expected ansible_playbook, got: " + sql);
    assertTrue(lower.contains("restart"), "expected playbook name in sql, got: " + sql);
  }

  @Test
  void createPolicyUsesAnsiblePolicyTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqAnsibleRepository repository =
        new JooqAnsibleRepository(
            DSL.using(
                new MockConnection(provider),
                SQLDialect.POSTGRES,
                new Settings().withStatementType(StatementType.STATIC_STATEMENT)));

    repository.createPolicy(
        new AnsiblePolicyCreateCommand(
            "apol_1",
            "tenant_1",
            "pb_1",
            true,
            true,
            true,
            true,
            "[\"inv_1\"]",
            "[\"service_name\"]",
            "[\"low\",\"medium\"]",
            "[]",
            true,
            32768,
            1800,
            3600,
            true));

    String sql = sqlRef.get();
    if (sql == null) {
      throw new AssertionError("SQL was not captured");
    }
    String lower = sql.toLowerCase();

    assertTrue(lower.contains("insert into"), "expected insert into, got: " + sql);
    assertTrue(
        lower.contains("ansible_execution_policy"),
        "expected ansible_execution_policy, got: " + sql);
    assertTrue(lower.contains("service_name"), "expected extra var in sql, got: " + sql);
    assertTrue(
        lower.contains("allow_check_execution"),
        "expected allow_check_execution in sql, got: " + sql);
  }
}
