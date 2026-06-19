package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.execution.dto.ExecutionAuditEventCreateCommand;
import io.aegisops.execution.dto.ExecutionReportCreateCommand;
import io.aegisops.execution.dto.ExecutionReportSectionCreateCommand;
import io.aegisops.execution.dto.ExecutionVerificationCreateCommand;
import java.util.concurrent.atomic.AtomicReference;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class JooqExecutionReportRepositoryGeneratedSqlTest {
  @Test
  void createReportUsesExecutionReportTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqExecutionReportRepository repository =
        new JooqExecutionReportRepository(
            DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.createReport(
        new ExecutionReportCreateCommand(
            "exr_1",
            "tenant_1",
            "exec_1",
            "standard",
            "generated",
            "title",
            "summary",
            "# md",
            "alice"));

    String sql = sqlRef.get();
    if (sql == null) {
      throw new AssertionError("SQL was not captured");
    }
    sql = sql.toLowerCase();

    assertTrue(sql.contains("insert into"), "expected insert into, got: " + sql);
    assertTrue(sql.contains("execution_report"), "expected execution_report, got: " + sql);
    assertTrue(sql.contains("markdown"), "expected markdown column, got: " + sql);
  }

  @Test
  void createSectionUsesExecutionReportSectionTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqExecutionReportRepository repository =
        new JooqExecutionReportRepository(
            DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.createSection(
        new ExecutionReportSectionCreateCommand(
            "exrs_1", "tenant_1", "exr_1", 1, "summary", "Summary", "content", "{}"));

    String sql = sqlRef.get();
    if (sql == null) {
      throw new AssertionError("SQL was not captured");
    }
    sql = sql.toLowerCase();

    assertTrue(sql.contains("insert into"), "expected insert into, got: " + sql);
    assertTrue(
        sql.contains("execution_report_section"), "expected execution_report_section, got: " + sql);
    assertTrue(sql.contains("section_order"), "expected section_order column, got: " + sql);
  }

  @Test
  void createVerificationUsesExecutionVerificationTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqExecutionReportRepository repository =
        new JooqExecutionReportRepository(
            DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.createVerification(
        new ExecutionVerificationCreateCommand(
            "exv_1",
            "tenant_1",
            "exec_1",
            null,
            "after",
            "service",
            "order-service",
            "passed",
            "ok",
            "{}",
            "alice"));

    String sql = sqlRef.get();
    if (sql == null) {
      throw new AssertionError("SQL was not captured");
    }
    sql = sql.toLowerCase();

    assertTrue(sql.contains("insert into"), "expected insert into, got: " + sql);
    assertTrue(
        sql.contains("execution_verification"), "expected execution_verification, got: " + sql);
    assertTrue(sql.contains("verification_type"), "expected verification_type column, got: " + sql);
  }

  @Test
  void createAuditEventUsesExecutionAuditEventTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqExecutionReportRepository repository =
        new JooqExecutionReportRepository(
            DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.createAuditEvent(
        new ExecutionAuditEventCreateCommand(
            "xae_1",
            "tenant_1",
            "exec_1",
            null,
            "report_generated",
            "alice",
            "report generated",
            "{}"));

    String sql = sqlRef.get();
    if (sql == null) {
      throw new AssertionError("SQL was not captured");
    }
    sql = sql.toLowerCase();

    assertTrue(sql.contains("insert into"), "expected insert into, got: " + sql);
    assertTrue(
        sql.contains("execution_audit_event"), "expected execution_audit_event, got: " + sql);
    assertTrue(sql.contains("event_type"), "expected event_type column, got: " + sql);
  }
}
