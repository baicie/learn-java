package io.aegisops.runbook;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.runbook.dto.ApprovalDecisionCommand;
import io.aegisops.runbook.dto.AutomationApprovalCreateCommand;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicReference;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class JooqApprovalRepositoryGeneratedSqlTest {
  @Test
  void createApprovalUsesAutomationApprovalTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqRunbookRepository repository =
        new JooqRunbookRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.createApproval(
        new AutomationApprovalCreateCommand(
            "approval_1",
            "tenant_1",
            "inc_1",
            "plan_1",
            "pending",
            "medium",
            1,
            0,
            0,
            "alice",
            OffsetDateTime.parse("2026-06-17T10:00:00+09:00"),
            null,
            "reason"));

    String sql = sqlRef.get().toLowerCase();

    assertTrue(sql.contains("insert into"));
    assertTrue(sql.contains("automation_approval"));
    assertTrue(sql.contains("required_approvals"));
    assertTrue(sql.contains("submitted_by"));
  }

  @Test
  void createDecisionUsesApprovalDecisionTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqRunbookRepository repository =
        new JooqRunbookRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.createDecision(
        new ApprovalDecisionCommand(
            "decision_1",
            "tenant_1",
            "approval_1",
            "plan_1",
            "reviewer_1",
            "approve",
            "ok",
            OffsetDateTime.parse("2026-06-17T10:00:00+09:00")));

    String sql = sqlRef.get().toLowerCase();

    assertTrue(sql.contains("insert into"));
    assertTrue(sql.contains("approval_decision"));
    assertTrue(sql.contains("reviewer"));
    assertTrue(sql.contains("decision"));
  }
}
