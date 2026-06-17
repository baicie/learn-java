package io.aegisops.persistence;

import static io.aegisops.persistence.jooq.Tables.AGENT_EVAL_RESULT;
import static io.aegisops.persistence.jooq.Tables.AGENT_RUN;
import static io.aegisops.persistence.jooq.Tables.AGENT_RUN_STEP;
import static io.aegisops.persistence.jooq.Tables.AI_DIAGNOSIS;
import static io.aegisops.persistence.jooq.Tables.ALERT_EVENT;
import static io.aegisops.persistence.jooq.Tables.ASSET_RELATION;
import static io.aegisops.persistence.jooq.Tables.CHANGE_EVENT;
import static io.aegisops.persistence.jooq.Tables.INCIDENT;
import static io.aegisops.persistence.jooq.Tables.INCIDENT_EVENT;
import static io.aegisops.persistence.jooq.Tables.INCIDENT_TIMELINE;
import static io.aegisops.persistence.jooq.Tables.LOG_EVENT;
import static io.aegisops.persistence.jooq.Tables.RCA_ANALYSIS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.jooq.JSONB;
import org.junit.jupiter.api.Test;

class JooqGeneratedRepositoryCoverageTest {
  @Test
  void generatedTablesCoverAiRepositoryTables() {
    assertNotNull(INCIDENT);
    assertNotNull(INCIDENT_EVENT);
    assertNotNull(ALERT_EVENT);
    assertNotNull(RCA_ANALYSIS);
    assertNotNull(AI_DIAGNOSIS);
    assertNotNull(INCIDENT_TIMELINE);
    assertNotNull(AGENT_RUN);
    assertNotNull(AGENT_RUN_STEP);
    assertNotNull(AGENT_EVAL_RESULT);
  }

  @Test
  void generatedTablesCoverRcaAndEvidenceTables() {
    assertNotNull(ASSET_RELATION);
    assertNotNull(LOG_EVENT);
    assertNotNull(CHANGE_EVENT);
  }

  @Test
  void generatedJsonColumnsRemainJsonb() {
    assertEquals(JSONB.class, ALERT_EVENT.LABELS.getType());
    assertEquals(JSONB.class, RCA_ANALYSIS.EVIDENCE.getType());
    assertEquals(JSONB.class, AI_DIAGNOSIS.REQUEST_PAYLOAD.getType());
    assertEquals(JSONB.class, AI_DIAGNOSIS.RESPONSE_RAW.getType());
    assertEquals(JSONB.class, AI_DIAGNOSIS.NEXT_STEPS.getType());
    assertEquals(JSONB.class, AGENT_RUN.SAFETY.getType());
    assertEquals(JSONB.class, AGENT_RUN_STEP.METADATA.getType());
    assertEquals(JSONB.class, AGENT_EVAL_RESULT.DETAILS.getType());
    assertEquals(JSONB.class, LOG_EVENT.ATTRIBUTES.getType());
    assertEquals(JSONB.class, CHANGE_EVENT.ATTRIBUTES.getType());
  }
}
