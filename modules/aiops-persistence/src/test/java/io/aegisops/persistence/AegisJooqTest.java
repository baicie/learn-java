package io.aegisops.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

class AegisJooqTest {
  @Test
  void jsonbValueRendersPostgresCast() {
    String sql = DSL.using(SQLDialect.POSTGRES).renderInlined(AegisJooq.jsonbValue("{\"a\":1}"));

    assertTrue(sql.contains("::jsonb"));
    assertTrue(sql.contains("{\"a\":1}"));
  }

  @Test
  void jsonbArrayValueFallsBackToEmptyArrayForBlank() {
    String blank = DSL.using(SQLDialect.POSTGRES).renderInlined(AegisJooq.jsonbArrayValue(""));
    String nullStr = DSL.using(SQLDialect.POSTGRES).renderInlined(AegisJooq.jsonbArrayValue(null));
    String nonEmpty =
        DSL.using(SQLDialect.POSTGRES).renderInlined(AegisJooq.jsonbArrayValue("[1]"));

    assertTrue(blank.contains("[]"));
    assertTrue(nullStr.contains("[]"));
    assertTrue(nonEmpty.contains("[1]"));
  }

  @Test
  void jsonArrayOrEmptyReturnsEmptyArrayForBlank() {
    assertEquals("[]", AegisJooq.jsonArrayOrEmpty(""));
    assertEquals("[]", AegisJooq.jsonArrayOrEmpty(null));
    assertEquals("[1]", AegisJooq.jsonArrayOrEmpty("[1]"));
  }

  @Test
  void jsonObjectOrEmptyReturnsEmptyObjectForBlank() {
    assertEquals("{}", AegisJooq.jsonObjectOrEmpty(""));
    assertEquals("{}", AegisJooq.jsonObjectOrEmpty(null));
    assertEquals("{\"a\":1}", AegisJooq.jsonObjectOrEmpty("{\"a\":1}"));
  }
}
