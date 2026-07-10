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
  void shouldBuildTextContainsWithParameterizedKeyAndValue() {
    StringBuilder where = new StringBuilder();
    Map<String, Object> params = new HashMap<>();

    builder.appendFilters(
        where,
        params,
        List.of(filter("content", DynamicFilterOperator.CONTAINS, FieldType.TEXT, "err", List.of())));

    assertThat(where.toString()).contains("custom_data_json ->> :dfKey0 ilike :dfValue0");
    assertThat(where.toString()).doesNotContain("content'");
    assertThat(params).containsEntry("dfKey0", "content");
    assertThat(params).containsEntry("dfValue0", "%err%");
  }

  @Test
  void shouldBuildNumberGteAsNumericComparison() {
    StringBuilder where = new StringBuilder();
    Map<String, Object> params = new HashMap<>();

    builder.appendFilters(
        where,
        params,
        List.of(filter("cost", DynamicFilterOperator.GTE, FieldType.NUMBER, "12.5", List.of())));

    assertThat(where.toString()).contains("::numeric >= cast(:dfValue0 as numeric)");
    assertThat(params).containsEntry("dfKey0", "cost");
    assertThat(params).containsEntry("dfValue0", "12.5");
  }

  @Test
  void shouldBuildDateBetweenAsDateComparison() {
    StringBuilder where = new StringBuilder();
    Map<String, Object> params = new HashMap<>();

    builder.appendFilters(
        where,
        params,
        List.of(filter("day", DynamicFilterOperator.BETWEEN, FieldType.DATE, null,
            List.of("2026-01-01", "2026-01-31"))));

    assertThat(where.toString())
        .contains("::date between cast(:dfFrom0 as date) and cast(:dfTo0 as date)");
  }

  @Test
  void shouldBuildDatetimeLteAsTimestamptzComparison() {
    StringBuilder where = new StringBuilder();
    Map<String, Object> params = new HashMap<>();

    builder.appendFilters(
        where,
        params,
        List.of(filter("startedAt", DynamicFilterOperator.LTE, FieldType.DATETIME,
            "2026-01-01T00:00Z", List.of())));

    assertThat(where.toString()).contains("::timestamptz <= cast(:dfValue0 as timestamptz)");
  }

  @Test
  void shouldBuildMultiSelectContainsAllWithJsonbContainment() {
    StringBuilder where = new StringBuilder();
    Map<String, Object> params = new HashMap<>();

    builder.appendFilters(
        where,
        params,
        List.of(filter("tags", DynamicFilterOperator.CONTAINS_ALL, FieldType.MULTI_SELECT, null,
            List.of("a", "b"))));

    assertThat(where.toString()).contains("@> cast(:dfJson0 as jsonb)");
    assertThat(params).containsEntry("dfJson0", "[\"a\",\"b\"]");
  }

  @Test
  void shouldBuildMultiSelectContainsAnyWithArrayElementsText() {
    StringBuilder where = new StringBuilder();
    Map<String, Object> params = new HashMap<>();

    builder.appendFilters(
        where,
        params,
        List.of(filter("tags", DynamicFilterOperator.CONTAINS_ANY, FieldType.MULTI_SELECT, null,
            List.of("a", "b"))));

    assertThat(where.toString()).contains("jsonb_array_elements_text");
    assertThat(where.toString()).contains("where x.value in (:dfList0)");
    assertThat(params.get("dfList0")).isEqualTo(List.of("a", "b"));
  }

  @Test
  void shouldBuildExistsAndNotExists() {
    StringBuilder where = new StringBuilder();
    Map<String, Object> params = new HashMap<>();

    builder.appendFilters(
        where,
        params,
        List.of(
            filter("priority", DynamicFilterOperator.EXISTS, FieldType.SELECT, null, List.of()),
            filter("tags", DynamicFilterOperator.NOT_EXISTS, FieldType.MULTI_SELECT, null, List.of())));

    assertThat(where.toString()).contains("custom_data_json ? :dfKey0");
    assertThat(where.toString()).contains("not (custom_data_json ? :dfKey1)");
  }

  @Test
  void shouldBuildBooleanEq() {
    StringBuilder where = new StringBuilder();
    Map<String, Object> params = new HashMap<>();

    builder.appendFilters(
        where,
        params,
        List.of(filter("urgent", DynamicFilterOperator.EQ, FieldType.BOOLEAN, "true", List.of())));

    assertThat(where.toString()).contains("custom_data_json ->> :dfKey0 = :dfValue0");
    assertThat(params).containsEntry("dfValue0", "true");
  }

  @Test
  void shouldBuildSelectIn() {
    StringBuilder where = new StringBuilder();
    Map<String, Object> params = new HashMap<>();

    builder.appendFilters(
        where,
        params,
        List.of(filter("severity", DynamicFilterOperator.IN, FieldType.SELECT, null,
            List.of("open", "in_progress"))));

    assertThat(where.toString()).contains("custom_data_json ->> :dfKey0 in (:dfList0)");
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
  void shouldHandleEmptyFiltersGracefully() {
    StringBuilder where = new StringBuilder(" where tenant_id = :tenantId ");
    Map<String, Object> params = new HashMap<>();
    params.put("tenantId", "t1");

    builder.appendFilters(where, params, List.of());

    assertThat(where.toString()).isEqualTo(" where tenant_id = :tenantId ");
    assertThat(params).hasSize(1);
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
