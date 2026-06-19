package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.execution.dto.PostmortemActionItemCreateCommand;
import io.aegisops.execution.dto.PostmortemReportCreateCommand;
import io.aegisops.execution.dto.PostmortemSectionCreateCommand;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class JooqPostmortemRepositoryGeneratedSqlTest {
  @Test
  void createReportUsesPostmortemReportTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqPostmortemRepository repository =
        new JooqPostmortemRepository(
            DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.createReport(
        new PostmortemReportCreateCommand(
            "pmr_1",
            "tenant_1",
            "inc_1",
            "generated",
            "high",
            "title",
            "summary",
            "impact",
            "root cause",
            "detection",
            "resolution",
            "prevention",
            "# md",
            "{}",
            "alice"));

    String sql = sqlRef.get();
    if (sql == null) {
      throw new AssertionError("SQL was not captured");
    }
    sql = sql.toLowerCase();

    assertTrue(sql.contains("insert into"), "expected insert into, got: " + sql);
    assertTrue(sql.contains("postmortem_report"), "expected postmortem_report, got: " + sql);
    assertTrue(sql.contains("source_snapshot"), "expected source_snapshot, got: " + sql);
  }

  @Test
  void createSectionUsesPostmortemSectionTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqPostmortemRepository repository =
        new JooqPostmortemRepository(
            DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.createSection(
        new PostmortemSectionCreateCommand(
            "pms_1",
            "tenant_1",
            "pmr_1",
            1,
            "summary",
            "Summary",
            "content",
            "{}"));

    String sql = sqlRef.get();
    if (sql == null) {
      throw new AssertionError("SQL was not captured");
    }
    sql = sql.toLowerCase();

    assertTrue(sql.contains("insert into"), "expected insert into, got: " + sql);
    assertTrue(sql.contains("postmortem_section"), "expected postmortem_section, got: " + sql);
    assertTrue(sql.contains("section_order"), "expected section_order, got: " + sql);
  }

  @Test
  void createActionItemUsesPostmortemActionItemTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqPostmortemRepository repository =
        new JooqPostmortemRepository(
            DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.createActionItem(
        new PostmortemActionItemCreateCommand(
            "pmai_1",
            "tenant_1",
            "pmr_1",
            "Update runbook",
            "desc",
            "bob",
            "high",
            "open",
            LocalDate.now().plusDays(7),
            "manual",
            null,
            "alice"));

    String sql = sqlRef.get();
    if (sql == null) {
      throw new AssertionError("SQL was not captured");
    }
    sql = sql.toLowerCase();

    assertTrue(sql.contains("insert into"), "expected insert into, got: " + sql);
    assertTrue(
        sql.contains("postmortem_action_item"), "expected postmortem_action_item, got: " + sql);
    assertTrue(sql.contains("priority"), "expected priority, got: " + sql);
  }
}
