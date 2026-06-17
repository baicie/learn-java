package io.aegisops.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

class AegisTablesTest {
  @Test
  void qualifiedFieldUsesAlias() {
    String sql = DSL.using(SQLDialect.POSTGRES).render(AegisTables.str(AegisTables.INCIDENT, "id"));

    assertTrue(sql.contains("i"));
    assertTrue(sql.contains("id"));
  }

  @Test
  void tableAliasIsStable() {
    assertEquals("i", AegisTables.INCIDENT.getName());
    assertEquals("ad", AegisTables.AI_DIAGNOSIS.getName());
    assertEquals("agr", AegisTables.AGENT_RUN.getName());
    assertEquals("ce", AegisTables.CHANGE_EVENT.getName());
  }

  @Test
  void jsonbFieldIsTypedAsJsonb() {
    var field = AegisTables.jsonb(AegisTables.AI_DIAGNOSIS, "response_raw");
    String sql = DSL.using(SQLDialect.POSTGRES).render(field);

    assertTrue(sql.contains("response_raw"));
    assertTrue(sql.contains("ad"));
  }
}
