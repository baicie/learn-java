package io.aegisops.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.evidence.dto.EvidenceQueryRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class JdbcEvidenceRepositoryJooqSqlTest {
  @Test
  void queryChangesUsesJooqInConditionForServiceNames() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JdbcEvidenceRepository repository =
        new JdbcEvidenceRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.queryChanges(request(), 10);

    String sql = sqlRef.get().toLowerCase();

    assertTrue(sql.contains("service_name"));
    assertTrue(sql.contains("asset_id"));
    assertTrue(sql.contains("change_event"));
  }

  @Test
  void queryLogsReturnsUnavailableWhenNoEntityScope() {
    JdbcEvidenceRepository repository = new JdbcEvidenceRepository(DSL.using(SQLDialect.POSTGRES));

    var result =
        repository.queryLogs(
            new EvidenceQueryRequest(
                "agent-diagnosis.v1",
                "tenant_1",
                "inc_1",
                "trace_1",
                null,
                OffsetDateTime.parse("2026-06-16T09:00:00+09:00"),
                OffsetDateTime.parse("2026-06-16T10:00:00+09:00"),
                List.of(),
                List.of(),
                List.of()),
            10);

    assertTrue(result.reason().contains("Primary asset id and service names are empty"));
  }

  @Test
  void queryLogsMapsCountAsLongAndSelectsLogCountAlias() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    Field<String> severityField = DSL.field("severity", String.class);
    Field<String> sampleField = DSL.field("sample", String.class);
    Field<Integer> logCountField = DSL.field("log_count", Integer.class);
    Field<OffsetDateTime> firstSeenField = DSL.field("first_seen_at", OffsetDateTime.class);
    Field<OffsetDateTime> lastSeenField = DSL.field("last_seen_at", OffsetDateTime.class);

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          var dsl = DSL.using(SQLDialect.POSTGRES);

          var result =
              dsl.newResult(
                  severityField, sampleField, logCountField, firstSeenField, lastSeenField);

          var row =
              dsl.newRecord(
                  severityField, sampleField, logCountField, firstSeenField, lastSeenField);
          row.set(severityField, "error");
          row.set(sampleField, "timeout");
          row.set(logCountField, 3);
          row.set(firstSeenField, OffsetDateTime.parse("2026-06-16T09:00:00+09:00"));
          row.set(lastSeenField, OffsetDateTime.parse("2026-06-16T10:00:00+09:00"));
          result.add(row);

          return new MockResult[] {new MockResult(1, result)};
        };

    JdbcEvidenceRepository repository =
        new JdbcEvidenceRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    var result = repository.queryLogs(request(), 10);

    assertTrue(result.available());
    assertEquals(1, result.patterns().size());
    assertEquals(3L, result.patterns().get(0).count());

    String sql = sqlRef.get().toLowerCase();
    assertTrue(sql.contains("log_count"));
  }

  private EvidenceQueryRequest request() {
    return new EvidenceQueryRequest(
        "agent-diagnosis.v1",
        "tenant_1",
        "inc_1",
        "trace_1",
        "asset_1",
        OffsetDateTime.parse("2026-06-16T09:00:00+09:00"),
        OffsetDateTime.parse("2026-06-16T10:00:00+09:00"),
        List.of("fp_cpu"),
        List.of("CPU high"),
        List.of("checkout-service"));
  }
}
