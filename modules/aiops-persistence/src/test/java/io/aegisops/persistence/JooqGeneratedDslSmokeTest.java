package io.aegisops.persistence;

import static io.aegisops.persistence.jooq.Tables.ALERT_EVENT;
import static io.aegisops.persistence.jooq.Tables.INCIDENT;
import static io.aegisops.persistence.jooq.Tables.INCIDENT_EVENT;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

class JooqGeneratedDslSmokeTest {
  @Test
  void generatedJoinDslRendersExpectedTables() {
    String sql =
        DSL.using(SQLDialect.POSTGRES)
            .select(ALERT_EVENT.ID, ALERT_EVENT.TITLE)
            .from(INCIDENT_EVENT)
            .join(INCIDENT)
            .on(INCIDENT.ID.eq(INCIDENT_EVENT.INCIDENT_ID))
            .join(ALERT_EVENT)
            .on(ALERT_EVENT.ID.eq(INCIDENT_EVENT.EVENT_ID))
            .where(INCIDENT.TENANT_ID.eq("tenant_1"))
            .and(INCIDENT_EVENT.EVENT_TYPE.eq("alert"))
            .getSQL()
            .toLowerCase();

    assertTrue(sql.contains("incident_event"));
    assertTrue(sql.contains("incident"));
    assertTrue(sql.contains("alert_event"));
    assertTrue(sql.contains("tenant_id"));
  }
}
