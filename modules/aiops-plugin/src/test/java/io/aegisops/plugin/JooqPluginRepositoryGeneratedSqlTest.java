package io.aegisops.plugin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.plugin.dto.PluginDescriptorCreateCommand;
import io.aegisops.plugin.dto.PluginEventCommand;
import io.aegisops.plugin.dto.ToolPolicyCommand;
import java.util.concurrent.atomic.AtomicReference;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class JooqPluginRepositoryGeneratedSqlTest {
  @Test
  void upsertDescriptorCapturesPluginDescriptorTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    var repository =
        new JooqPluginRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.upsertDescriptor(
        new PluginDescriptorCreateCommand(
            "plg_1",
            "builtin.test",
            "Test",
            "0.1.0",
            "desc",
            "builtin",
            "active",
            "{}",
            "{}",
            "system"));

    String sql = sqlRef.get().toLowerCase();
    assertTrue(sql.contains("plugin_descriptor"), "SQL should reference plugin_descriptor: " + sql);
  }

  @Test
  void createEventCapturesPluginEventTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    var repository =
        new JooqPluginRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.createEvent(
        new PluginEventCommand(
            "ple_1",
            "tenant_1",
            "plg_1",
            "tplg_1",
            "plugin_registered",
            "registered",
            "system",
            "{}"));

    String sql = sqlRef.get().toLowerCase();
    assertTrue(sql.contains("plugin_event"), "SQL should reference plugin_event: " + sql);
  }

  @Test
  void upsertToolPolicyCapturesTenantPluginToolPolicyTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    var repository =
        new JooqPluginRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.upsertToolPolicy(
        new ToolPolicyCommand(
            "tptp_1", "tenant_1", "tplg_1", "plg_1", "memory.search", "allowed", "low", "system"));

    String sql = sqlRef.get().toLowerCase();
    assertTrue(
        sql.contains("tenant_plugin_tool_policy"),
        "SQL should reference tenant_plugin_tool_policy: " + sql);
  }
}
