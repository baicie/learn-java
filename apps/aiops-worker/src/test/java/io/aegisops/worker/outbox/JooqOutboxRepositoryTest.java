package io.aegisops.worker.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class JooqOutboxRepositoryTest {

  @Test
  void timestampParametersAreExplicitlyTypedForPostgres() {
    List<String> sql = new ArrayList<>();
    MockConnection connection =
        new MockConnection(
            context -> {
              sql.add(context.sql());
              return new MockResult[] {new MockResult(1)};
            });
    JooqOutboxRepository repository =
        new JooqOutboxRepository(DSL.using(connection, SQLDialect.POSTGRES));
    OffsetDateTime now = OffsetDateTime.parse("2026-07-15T12:00:00+08:00");

    repository.claimNextPending("worker", 10, now);
    repository.recoverExpiredLeases("worker", now);
    repository.extendLease("outbox-1", "worker", now);
    repository.markDone("outbox-1", now);

    assertThat(sql)
        .allSatisfy(
            statement ->
                assertThat(statement)
                    .containsPattern("cast\\(\\? as timestamp(?:tz|\\([0-9]+\\) with time zone)"));
  }
}
