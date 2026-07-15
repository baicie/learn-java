package io.aegisops.worker.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicReference;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class JooqOutboxRepositoryTest {

  @Test
  void recoverExpiredLeasesUsesTimestampWithTimezoneParameter() {
    AtomicReference<String> sql = new AtomicReference<>();
    MockConnection connection =
        new MockConnection(
            context -> {
              sql.set(context.sql());
              return new MockResult[] {new MockResult(1)};
            });
    JooqOutboxRepository repository =
        new JooqOutboxRepository(DSL.using(connection, SQLDialect.POSTGRES));
    OffsetDateTime now = OffsetDateTime.parse("2026-07-15T12:00:00+08:00");

    assertThat(repository.recoverExpiredLeases("worker", now)).isEqualTo(1);

    assertThat(sql.get()).contains("cast(? as timestamp");
  }
}
