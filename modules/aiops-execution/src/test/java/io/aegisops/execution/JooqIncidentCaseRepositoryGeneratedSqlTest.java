package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.execution.dto.IncidentCaseCreateCommand;
import io.aegisops.execution.dto.IncidentCaseResolutionStepCreateCommand;
import io.aegisops.execution.dto.IncidentCaseSymptomCreateCommand;
import io.aegisops.execution.dto.IncidentCaseTagCreateCommand;
import java.util.concurrent.atomic.AtomicReference;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class JooqIncidentCaseRepositoryGeneratedSqlTest {
  @Test
  void createCaseUsesIncidentCaseTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    var repository =
        new JooqIncidentCaseRepository(
            DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.createCase(
        new IncidentCaseCreateCommand(
            "icase_1",
            "tenant_1",
            "pmr_1",
            "inc_1",
            "draft",
            "high",
            "title",
            "summary",
            "root cause",
            "resolution",
            "prevention",
            80,
            "alice"));

    String sql = sqlRef.get();
    if (sql == null) {
      throw new AssertionError("SQL was not captured");
    }
    sql = sql.toLowerCase();

    assertTrue(sql.contains("incident_case"), "expected incident_case, got: " + sql);
    assertTrue(sql.contains("source_postmortem_id"), "expected source_postmortem_id, got: " + sql);
  }

  @Test
  void createSymptomUsesIncidentCaseSymptomTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    var repository =
        new JooqIncidentCaseRepository(
            DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.createSymptom(
        new IncidentCaseSymptomCreateCommand(
            "icsym_1", "tenant_1", "icase_1", "impact", "High error rate", "desc"));

    String sql = sqlRef.get();
    if (sql == null) {
      throw new AssertionError("SQL was not captured");
    }
    sql = sql.toLowerCase();

    assertTrue(
        sql.contains("incident_case_symptom"), "expected incident_case_symptom, got: " + sql);
  }

  @Test
  void createResolutionStepUsesTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    var repository =
        new JooqIncidentCaseRepository(
            DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.createResolutionStep(
        new IncidentCaseResolutionStepCreateCommand(
            "icstep_1", "tenant_1", "icase_1", 1, "Restart service", "desc", "manual", "pms_1"));

    String sql = sqlRef.get();
    if (sql == null) {
      throw new AssertionError("SQL was not captured");
    }
    sql = sql.toLowerCase();

    assertTrue(
        sql.contains("incident_case_resolution_step"),
        "expected incident_case_resolution_step, got: " + sql);
  }

  @Test
  void createTagUsesTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    var repository =
        new JooqIncidentCaseRepository(
            DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.createTag(
        new IncidentCaseTagCreateCommand("ictag_1", "tenant_1", "icase_1", "order-service"));

    String sql = sqlRef.get();
    if (sql == null) {
      throw new AssertionError("SQL was not captured");
    }
    sql = sql.toLowerCase();

    assertTrue(sql.contains("incident_case_tag"), "expected incident_case_tag, got: " + sql);
  }
}
