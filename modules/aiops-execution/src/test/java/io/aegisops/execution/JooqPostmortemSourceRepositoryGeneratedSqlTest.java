package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.conf.StatementType;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class JooqPostmortemSourceRepositoryGeneratedSqlTest {
  @Test
  void loadScopesTimelineByTenantViaJoin() throws Exception {
    AtomicReference<String> allSqlRef = new AtomicReference<>("");

    MockDataProvider provider =
        context -> {
          allSqlRef.updateAndGet(prev -> prev + "\n" + context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    var dsl =
        DSL.using(
            new MockConnection(provider),
            SQLDialect.POSTGRES,
            new Settings().withStatementType(StatementType.STATIC_STATEMENT));
    var repository = new JooqPostmortemSourceRepository(dsl);

    // Call loadTimeline directly via reflection (it's private).
    // This bypasses the incident existence check so we can test timeline SQL in isolation.
    Method loadTimeline =
        JooqPostmortemSourceRepository.class.getDeclaredMethod(
            "loadTimeline", String.class, String.class);
    loadTimeline.setAccessible(true);
    try {
      loadTimeline.invoke(repository, "tenant_1", "inc_1");
    } catch (Exception ignored) {
    }

    String allSql = allSqlRef.get().toLowerCase();

    assertTrue(allSql.contains("join"), "expected join in captured SQL, got: " + allSql);
    assertTrue(
        allSql.contains("incident"), "expected incident join in captured SQL, got: " + allSql);
    assertTrue(allSql.contains("tenant_id"), "expected tenant_id in captured SQL, got: " + allSql);
    assertTrue(
        allSql.contains("incident_timeline"),
        "expected incident_timeline in captured SQL, got: " + allSql);
  }
}
