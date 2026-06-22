package io.aegisops.ai.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import io.aegisops.ai.client.dto.SaveDiagnosisCommand;
import java.io.PrintWriter;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JooqAiRepositoryGeneratedSqlTest {
  private static JdbcTemplate emptyJdbcTemplate() {
    return new JdbcTemplate(new MockDataSource());
  }

  @Test
  void saveDiagnosisUsesGeneratedTableAndJsonbCasts() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqAiRepository repository =
        new JooqAiRepository(
            DSL.using(new MockConnection(provider), SQLDialect.POSTGRES), emptyJdbcTemplate());

    repository.saveDiagnosis(
        new SaveDiagnosisCommand(
            "diag_1",
            "tenant_1",
            "inc_1",
            new AgentDiagnosisResponse(
                "agent-diagnosis.v1",
                "aiops-agent",
                "langgraph-deterministic",
                "aegisops_diagnosis_graph",
                "summary",
                "root",
                "impact",
                List.of("step"),
                List.of(),
                List.of(),
                Map.of()),
            "{}",
            "{}",
            "[\"step\"]",
            "[]",
            "[]"));

    String sql = sqlRef.get().toLowerCase();

    assertTrue(sql.contains("insert into"));
    assertTrue(sql.contains("ai_diagnosis"));
    assertTrue(sql.contains("::jsonb"));
  }

  @Test
  void listIncidentAlertsRendersGeneratedJoinAndTenantGuard() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqAiRepository repository =
        new JooqAiRepository(
            DSL.using(new MockConnection(provider), SQLDialect.POSTGRES), emptyJdbcTemplate());

    repository.listIncidentAlerts("tenant_1", "inc_1");

    String sql = sqlRef.get().toLowerCase();

    assertTrue(sql.contains("incident_event"));
    assertTrue(sql.contains("incident"));
    assertTrue(sql.contains("alert_event"));
    assertTrue(sql.contains("tenant_id"));
    assertTrue(sql.contains("event_type"));
  }

  @Test
  void findLatestAgentRunRendersAgentRunQuery() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JooqAiRepository repository =
        new JooqAiRepository(
            DSL.using(new MockConnection(provider), SQLDialect.POSTGRES), emptyJdbcTemplate());

    repository.findLatestAgentRun("tenant_1", "inc_1");

    String sql = sqlRef.get().toLowerCase();

    assertTrue(sql.contains("agent_run"));
    assertTrue(sql.contains("tenant_id"));
    assertTrue(sql.contains("incident_id"));
    assertTrue(sql.contains("created_at"));
  }

  private static final class MockDataSource implements DataSource {
    @Override
    public Connection getConnection() {
      throw new UnsupportedOperationException("not used in this test");
    }

    @Override
    public Connection getConnection(String username, String password) {
      throw new UnsupportedOperationException("not used in this test");
    }

    @Override
    public PrintWriter getLogWriter() {
      return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {}

    @Override
    public void setLoginTimeout(int seconds) {}

    @Override
    public int getLoginTimeout() {
      return 0;
    }

    @Override
    public java.util.logging.Logger getParentLogger() {
      return null;
    }

    @Override
    public <T> T unwrap(Class<T> iface) {
      return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
      return false;
    }
  }
}
