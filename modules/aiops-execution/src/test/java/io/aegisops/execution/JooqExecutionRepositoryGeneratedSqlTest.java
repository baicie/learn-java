package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.execution.dto.ExecutionRunCreateCommand;
import java.util.concurrent.atomic.AtomicReference;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class JooqExecutionRepositoryGeneratedSqlTest {
  @Test
  void createRunUsesExecutionRunTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqExecutionRepository repository =
        new JooqExecutionRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.createRun(
        new ExecutionRunCreateCommand(
            "exec_1", "tenant_1", "inc_1", "plan_1", "queued", "dry_run", "alice"));

    String sql = sqlRef.get();
    if (sql == null) {
      throw new AssertionError("SQL was not captured");
    }
    sql = sql.toLowerCase();

    assertTrue(sql.contains("insert into"), "expected insert into, got: " + sql);
    assertTrue(sql.contains("execution_run"), "expected execution_run, got: " + sql);
    assertTrue(sql.contains("status"), "expected status column, got: " + sql);
    assertTrue(sql.contains("mode"), "expected mode column, got: " + sql);
  }

  @Test
  void updatePlanStatusUsesAutomationPlanTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqExecutionRepository repository =
        new JooqExecutionRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.updatePlanStatus("tenant_1", "plan_1", "executing");

    String sql = sqlRef.get();
    if (sql == null) {
      throw new AssertionError("SQL was not captured");
    }
    sql = sql.toLowerCase();

    assertTrue(sql.contains("update"), "expected update, got: " + sql);
    assertTrue(sql.contains("automation_plan"), "expected automation_plan, got: " + sql);
    assertTrue(sql.contains("status"), "expected status column, got: " + sql);
  }
}
