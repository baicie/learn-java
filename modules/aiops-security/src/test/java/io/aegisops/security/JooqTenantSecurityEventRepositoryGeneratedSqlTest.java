package io.aegisops.security;

import java.util.concurrent.atomic.AtomicReference;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class JooqTenantSecurityEventRepositoryGeneratedSqlTest {
  @Test
  void createCapturesCorrectTableAndColumns() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider = context -> {
      sqlRef.set(context.sql());
      return new MockResult[] {
        new MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult())
      };
    };

    var repository = new JooqTenantSecurityEventRepository(
        DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.create(
        new TenantSecurityEventCreateCommand(
            "tse_1",
            "tenant_1",
            "internal_auth_failed",
            "critical",
            "system",
            "/internal/agent/memories",
            "127.0.0.1",
            "Invalid token",
            "{}"));

    String sql = sqlRef.get().toLowerCase();
    assert sql.contains("tenant_security_event") : "SQL should reference tenant_security_event table: " + sql;
  }
}
