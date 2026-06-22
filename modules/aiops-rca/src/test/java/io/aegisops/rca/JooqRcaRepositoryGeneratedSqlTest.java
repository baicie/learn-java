package io.aegisops.rca;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JooqRcaRepositoryGeneratedSqlTest {
  @Test
  void listAssetRelationsUsesGeneratedTablesAndInCondition() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqRcaRepository repository =
        new JooqRcaRepository(
            DSL.using(new MockConnection(provider), SQLDialect.POSTGRES), mock(JdbcTemplate.class));

    repository.listAssetRelations("tenant_1", List.of("asset_1", "asset_2"));

    String sql = sqlRef.get().toLowerCase();

    assertTrue(sql.contains("asset_relation"));
    assertTrue(sql.contains("from_asset_id"));
    assertTrue(sql.contains("to_asset_id"));
    assertTrue(sql.contains(" in "));
  }

  @Test
  void saveAnalysisUsesJsonbArrayForEvidence() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqRcaRepository repository =
        new JooqRcaRepository(
            DSL.using(new MockConnection(provider), SQLDialect.POSTGRES), mock(JdbcTemplate.class));

    repository.saveAnalysis(
        new SaveAnalysisParams(
            "rca_1",
            "tenant_1",
            "inc_1",
            "root",
            BigDecimal.valueOf(0.8),
            "summary",
            "",
            "rules-v1"));

    String sql = sqlRef.get().toLowerCase();

    assertTrue(sql.contains("insert into"));
    assertTrue(sql.contains("rca_analysis"));
    assertTrue(sql.contains("evidence"));
    assertTrue(sql.contains("?::jsonb"));
  }

  @Test
  void listIncidentAlertsRendersGeneratedJoinAndTenantGuard() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqRcaRepository repository =
        new JooqRcaRepository(
            DSL.using(new MockConnection(provider), SQLDialect.POSTGRES), mock(JdbcTemplate.class));

    repository.listIncidentAlerts("tenant_1", "inc_1");

    String sql = sqlRef.get().toLowerCase();

    assertTrue(sql.contains("incident_event"));
    assertTrue(sql.contains("incident"));
    assertTrue(sql.contains("alert_event"));
    assertTrue(sql.contains("tenant_id"));
    assertTrue(sql.contains("event_type"));
  }
}
