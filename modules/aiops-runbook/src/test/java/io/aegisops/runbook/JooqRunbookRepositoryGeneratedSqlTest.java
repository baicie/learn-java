package io.aegisops.runbook;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.runbook.dto.AutomationPlanCreateCommand;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class JooqRunbookRepositoryGeneratedSqlTest {
  @Test
  void listRunbooksUsesRunbookTableAndTenantScope() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqRunbookRepository repository =
        new JooqRunbookRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.listRunbooks("tenant_1", false);

    String sql = sqlRef.get().toLowerCase();

    assertTrue(sql.contains("runbook"));
    assertTrue(sql.contains("tenant_id"));
    assertTrue(sql.contains("enabled"));
  }

  @Test
  void createPlanUsesAutomationPlanTableAndJsonbEvidence() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqRunbookRepository repository =
        new JooqRunbookRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.createPlan(
        new AutomationPlanCreateCommand(
            "plan_1",
            "tenant_1",
            "inc_1",
            "rb_1",
            "diag_1",
            "rca_1",
            "runbook-recommendation-v1",
            "draft",
            "medium",
            BigDecimal.valueOf(0.8),
            "title",
            "summary",
            "{}",
            "system"));

    String sql = sqlRef.get().toLowerCase();

    assertTrue(sql.contains("insert into"));
    assertTrue(sql.contains("automation_plan"));
    assertTrue(sql.contains("evidence"));
    assertTrue(sql.contains("::jsonb"));
  }
}
