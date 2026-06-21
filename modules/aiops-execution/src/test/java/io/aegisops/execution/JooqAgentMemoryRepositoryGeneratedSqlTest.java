package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.execution.dto.AgentMemoryCreateCommand;
import io.aegisops.execution.dto.AgentMemoryEventCreateCommand;
import java.util.ArrayList;
import java.util.List;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class JooqAgentMemoryRepositoryGeneratedSqlTest {
  @Test
  void createMemoryAndEventUseTables() {
    List<String> capturedSql = new ArrayList<>();

    MockDataProvider provider =
        context -> {
          capturedSql.add(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    var repository = new JooqAgentMemoryRepository(DSL.using(new MockConnection(provider)));

    repository.create(
        new AgentMemoryCreateCommand(
            "agm_1",
            "tenant_1",
            "tenant",
            null,
            "root_cause_pattern",
            "diagnosis",
            "inc_1",
            "Redis timeout pattern",
            "Order service redis timeout.",
            "[\"redis\"]",
            0.8,
            "active",
            "agent",
            null));

    repository.createEvent(
        new AgentMemoryEventCreateCommand(
            "agme_1", "tenant_1", "agm_1", "created", "created", "agent", "{}"));

    String sql = String.join("\n", capturedSql).toLowerCase();

    assertTrue(sql.contains("agent_memory"), "expected agent_memory, got: " + sql);
    assertTrue(sql.contains("agent_memory_event"), "expected agent_memory_event, got: " + sql);
  }
}
