package io.aegisops.persistence;

import static io.aegisops.persistence.jooq.public_.Tables.AGENT_RUN;
import static io.aegisops.persistence.jooq.public_.Tables.AI_DIAGNOSIS;
import static io.aegisops.persistence.jooq.public_.Tables.CHANGE_EVENT;
import static io.aegisops.persistence.jooq.public_.Tables.INCIDENT;
import static io.aegisops.persistence.jooq.public_.Tables.LOG_EVENT;
import static io.aegisops.persistence.jooq.public_.Tables.RCA_ANALYSIS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.jooq.JSONB;
import org.junit.jupiter.api.Test;

class JooqGeneratedSchemaTest {
  @Test
  void generatedCoreTablesExist() {
    assertNotNull(INCIDENT);
    assertNotNull(RCA_ANALYSIS);
    assertNotNull(AI_DIAGNOSIS);
    assertNotNull(AGENT_RUN);
    assertNotNull(LOG_EVENT);
    assertNotNull(CHANGE_EVENT);
  }

  @Test
  void generatedColumnNamesMatchFlywaySchema() {
    assertEquals("tenant_id", INCIDENT.TENANT_ID.getName());
    assertEquals("evidence", RCA_ANALYSIS.EVIDENCE.getName());
    assertEquals("response_raw", AI_DIAGNOSIS.RESPONSE_RAW.getName());
    assertEquals("generation_mode", AGENT_RUN.GENERATION_MODE.getName());
    assertEquals("service_name", LOG_EVENT.SERVICE_NAME.getName());
    assertEquals("change_type", CHANGE_EVENT.CHANGE_TYPE.getName());
  }

  @Test
  void generatedJsonColumnsUseJsonbType() {
    assertEquals(JSONB.class, RCA_ANALYSIS.EVIDENCE.getType());
    assertEquals(JSONB.class, AI_DIAGNOSIS.RESPONSE_RAW.getType());
    assertEquals(JSONB.class, AGENT_RUN.SAFETY.getType());
    assertEquals(JSONB.class, LOG_EVENT.ATTRIBUTES.getType());
    assertEquals(JSONB.class, CHANGE_EVENT.ATTRIBUTES.getType());
  }
}
