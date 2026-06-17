package io.aegisops.ai.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import io.aegisops.ai.client.dto.SaveDiagnosisCommand;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class JdbcAiRepositoryJooqSqlTest {
  @Test
  void saveDiagnosisUsesJsonbCasts() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JdbcAiRepository repository =
        new JdbcAiRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

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
  void listIncidentAlertsRendersTenantGuard() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JdbcAiRepository repository =
        new JdbcAiRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.listIncidentAlerts("tenant_1", "inc_1");

    String sql = sqlRef.get().toLowerCase();

    assertTrue(sql.contains("incident_event"));
    assertTrue(sql.contains("alert_event"));
    assertTrue(sql.contains("tenant_id"));
  }
}
