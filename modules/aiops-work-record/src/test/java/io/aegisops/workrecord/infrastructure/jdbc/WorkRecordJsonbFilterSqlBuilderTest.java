package io.aegisops.workrecord.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.command.DynamicFilterOperator;
import io.aegisops.workrecord.application.command.RecordDynamicFilter;
import io.aegisops.workrecord.domain.model.FieldType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkRecordJsonbFilterSqlBuilderTest {
  private final WorkRecordJsonbFilterSqlBuilder builder =
      new WorkRecordJsonbFilterSqlBuilder(new ObjectMapper());

  @Test
  void shouldBuildTextContainsWithJsonbTypeofGuard() {
    StringBuilder where = new StringBuilder();
    Map<String, Object> params = new HashMap<>();

    builder.appendFilters(
        where,
        params,
        List.of(
            filter("content", DynamicFilterOperator.CONTAINS, FieldType.TEXT, "err", List.of())));

    assertThat(where.toString()).contains("jsonb_typeof");
    assertThat(where.toString()).contains("'string'");
    assertThat(where.toString()).contains("ilike");
    assertThat(where.toString()).doesNotContain("content'");
    assertThat(params).containsEntry("dfKey0", "content");
    assertThat(params).containsEntry("dfValue0", "%err%");
  }

  @Test
  void shouldBuildNumberGteWithSafeFunctionCall() {
    StringBuilder where = new StringBuilder();
    Map<String, Object> params = new HashMap<>();

    builder.appendFilters(
        where,
        params,
        List.of(filter("cost", DynamicFilterOperator.GTE, FieldType.NUMBER, "12.5", List.of())));

    assertThat(where.toString()).contains("try_numeric");
    assertThat(where.toString()).contains("'number'");
    assertThat(where.toString()).contains(">=");
    assertThat(params).containsEntry("dfKey0", "cost");
    assertThat(params).containsEntry("dfValue0", "12.5");
  }

  @Test
  void shouldBuildDateBetweenWithSafeFunctionCall() {
    StringBuilder where = new StringBuilder();
    Map<String, Object> params = new HashMap<>();

    builder.appendFilters(
        where,
        params,
        List.of(
            filter(
                "day",
                DynamicFilterOperator.BETWEEN,
                FieldType.DATE,
                null,
                List.of("2026-01-01", "2026-01-31"))));

    assertThat(where.toString()).contains("try_date");
    assertThat(where.toString()).contains("between");
    assertThat(params).containsEntry("dfFrom0", "2026-01-01");
    assertThat(params).containsEntry("dfTo0", "2026-01-31");
  }

  @Test
  void shouldBuildDatetimeLteWithSafeFunctionCall() {
    StringBuilder where = new StringBuilder();
    Map<String, Object> params = new HashMap<>();

    builder.appendFilters(
        where,
        params,
        List.of(
            filter(
                "startedAt",
                DynamicFilterOperator.LTE,
                FieldType.DATETIME,
                "2026-01-01T00:00Z",
                List.of())));

    assertThat(where.toString()).contains("try_timestamptz");
    assertThat(where.toString()).contains("<=");
  }

  @Test
  void shouldBuildMultiSelectContainsAllWithJsonbContainment() {
    StringBuilder where = new StringBuilder();
    Map<String, Object> params = new HashMap<>();

    builder.appendFilters(
        where,
        params,
        List.of(
            filter(
                "tags",
                DynamicFilterOperator.CONTAINS_ALL,
                FieldType.MULTI_SELECT,
                null,
                List.of("a", "b"))));

    assertThat(where.toString()).contains("@>");
    assertThat(params.get("dfJson0")).isEqualTo("[\"a\",\"b\"]");
  }

  @Test
  void shouldBuildMultiSelectContainsAnyWithIndividualContainmentChecks() {
    StringBuilder where = new StringBuilder();
    Map<String, Object> params = new HashMap<>();

    builder.appendFilters(
        where,
        params,
        List.of(
            filter(
                "tags",
                DynamicFilterOperator.CONTAINS_ANY,
                FieldType.MULTI_SELECT,
                null,
                List.of("a", "b"))));

    String sql = where.toString();
    assertThat(sql).contains(" and ("); // groups the OR-combined branches
    assertThat(sql).contains("jsonb_typeof");
    assertThat(sql).contains("'array'");
    assertThat(sql).contains(" or ");
    assertThat(sql).contains("jsonb_build_object");
    assertThat(params).containsEntry("dfAny0_0", "a");
    assertThat(params).containsEntry("dfAny0_1", "b");

    assertThat(count(sql, '(')).isEqualTo(count(sql, ')'));
    assertThat(count(sql, '\'')).isEqualTo(2);
  }

  @Test
  void containsAnyShouldProduceBalancedParameterizedSql() {
    StringBuilder where = new StringBuilder(" where tenant_id = :tenantId ");
    Map<String, Object> params = new HashMap<>();

    builder.appendFilters(
        where,
        params,
        List.of(
            filter(
                "tags",
                DynamicFilterOperator.CONTAINS_ANY,
                FieldType.MULTI_SELECT,
                null,
                List.of("a", "b"))));

    String sql = where.toString();

    assertThat(sql)
        .contains(" and (")
        .contains(" or ")
        .doesNotContain("'tags'")
        .doesNotContain("->>'tags'");

    assertThat(count(sql, '(')).isEqualTo(count(sql, ')'));

    assertThat(params)
        .containsEntry("dfKey0", "tags")
        .containsEntry("dfAny0_0", "a")
        .containsEntry("dfAny0_1", "b");
  }

  private static long count(String value, char target) {
    return value.chars().filter(character -> character == target).count();
  }

  @Test
  void shouldBuildExistsWithJsonbExistsFunction() {
    StringBuilder where = new StringBuilder();
    Map<String, Object> params = new HashMap<>();

    builder.appendFilters(
        where,
        params,
        List.of(
            filter("priority", DynamicFilterOperator.EXISTS, FieldType.SELECT, null, List.of())));

    assertThat(where.toString()).contains("jsonb_exists");
    assertThat(where.toString()).contains("cast(:" + "dfKey0 as text)");
    assertThat(where.toString()).doesNotContain(" ? ");
  }

  @Test
  void shouldBuildNotExistsWithJsonbExistsFunction() {
    StringBuilder where = new StringBuilder();
    Map<String, Object> params = new HashMap<>();

    builder.appendFilters(
        where,
        params,
        List.of(
            filter(
                "priority", DynamicFilterOperator.NOT_EXISTS, FieldType.SELECT, null, List.of())));

    assertThat(where.toString()).contains("not");
    assertThat(where.toString()).contains("jsonb_exists");
  }

  @Test
  void shouldBuildBooleanEqWithTypeGuard() {
    StringBuilder where = new StringBuilder();
    Map<String, Object> params = new HashMap<>();

    builder.appendFilters(
        where,
        params,
        List.of(filter("urgent", DynamicFilterOperator.EQ, FieldType.BOOLEAN, "true", List.of())));

    assertThat(where.toString()).contains("jsonb_typeof");
    assertThat(where.toString()).contains("'boolean'");
    assertThat(params).containsEntry("dfValue0", "true");
  }

  @Test
  void shouldBuildSelectInWithTypeGuard() {
    StringBuilder where = new StringBuilder();
    Map<String, Object> params = new HashMap<>();

    builder.appendFilters(
        where,
        params,
        List.of(
            filter(
                "severity",
                DynamicFilterOperator.IN,
                FieldType.SELECT,
                null,
                List.of("open", "in_progress"))));

    assertThat(where.toString()).contains("jsonb_typeof");
    assertThat(where.toString()).contains("'string'");
    assertThat(params.get("dfList0")).isEqualTo(List.of("open", "in_progress"));
  }

  @Test
  void shouldRejectUnnormalizedFilterWithoutFieldType() {
    assertThatThrownBy(
            () ->
                builder.appendFilters(
                    new StringBuilder(),
                    new HashMap<>(),
                    List.of(RecordDynamicFilter.raw("priority", "eq", "P1"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fieldType is required");
  }

  @Test
  void shouldRejectMultiSelectWithEqOperator() {
    assertThatThrownBy(
            () ->
                builder.appendFilters(
                    new StringBuilder(),
                    new HashMap<>(),
                    List.of(
                        filter(
                            "tags",
                            DynamicFilterOperator.EQ,
                            FieldType.MULTI_SELECT,
                            "a",
                            List.of()))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("operator eq is incompatible with multi_select");
  }

  @Test
  void shouldHandleEmptyFiltersGracefully() {
    StringBuilder where = new StringBuilder(" where tenant_id = :tenantId ");
    Map<String, Object> params = new HashMap<>();
    params.put("tenantId", "t1");

    builder.appendFilters(where, params, List.of());

    assertThat(where.toString()).isEqualTo(" where tenant_id = :tenantId ");
    assertThat(params).hasSize(1);
  }

  @Test
  void shouldRejectFilterWithNullFieldType() {
    assertThatThrownBy(
            () ->
                builder.appendFilters(
                    new StringBuilder(),
                    new HashMap<>(),
                    List.of(RecordDynamicFilter.raw("priority", "eq", "P1"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fieldType is required");
  }

  @Test
  void shouldRejectNullOperator() {
    assertThatThrownBy(
            () ->
                builder.appendFilters(
                    new StringBuilder(),
                    new HashMap<>(),
                    List.of(
                        RecordDynamicFilter.normalized(
                            "priority", null, FieldType.SELECT, "P1", List.of()))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("operator is required");
  }

  private RecordDynamicFilter filter(
      String code,
      DynamicFilterOperator operator,
      FieldType type,
      Object value,
      List<Object> values) {
    return RecordDynamicFilter.normalized(code, operator, type, value, values);
  }
}
