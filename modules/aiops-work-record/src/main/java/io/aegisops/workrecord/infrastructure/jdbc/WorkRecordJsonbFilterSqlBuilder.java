package io.aegisops.workrecord.infrastructure.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.command.DynamicFilterOperator;
import io.aegisops.workrecord.application.command.RecordDynamicFilter;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.rule.FieldCodeRules;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * JSONB 动态筛选 SQL 构建器。
 *
 * <p>所有生成的 SQL 片段使用 NamedParameterJdbcTemplate 参数化查询，JSONB key/value
 * 均通过参数传入，绝不直接拼接用户输入的 fieldCode 或 value。
 *
 * <p>支持的字段类型和操作符：
 *
 * <ul>
 *   <li>TEXT/TEXTAREA/SELECT/USER: EQ, CONTAINS, EXISTS, NOT_EXISTS
 *   <li>NUMBER/DATE/DATETIME: EQ, GTE, LTE, BETWEEN, EXISTS, NOT_EXISTS
 *   <li>BOOLEAN: EQ, EXISTS, NOT_EXISTS
 *   <li>MULTI_SELECT: IN, CONTAINS_ANY, CONTAINS_ALL, EXISTS, NOT_EXISTS
 * </ul>
 */
@Component
public class WorkRecordJsonbFilterSqlBuilder {
  private final ObjectMapper objectMapper;

  public WorkRecordJsonbFilterSqlBuilder(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * 将筛选条件追加到已有的 WHERE 子句。
   *
   * @param where 已有 WHERE 子句（不含前缀关键字）
   * @param params 命名参数映射
   * @param filters 已标准化的筛选条件列表
   */
  public void appendFilters(
      StringBuilder where,
      Map<String, Object> params,
      List<RecordDynamicFilter> filters) {
    if (filters == null || filters.isEmpty()) {
      return;
    }

    int index = 0;
    for (RecordDynamicFilter filter : filters) {
      appendFilter(where, params, filter, index);
      index += 1;
    }
  }

  private void appendFilter(
      StringBuilder where,
      Map<String, Object> params,
      RecordDynamicFilter filter,
      int index) {
    FieldCodeRules.validate(filter.fieldCode());

    if (filter.fieldType() == null) {
      throw new IllegalArgumentException(
          "normalized filter fieldType is required: " + filter.fieldCode());
    }

    String keyParam = "dfKey" + index;
    params.put(keyParam, filter.fieldCode());

    switch (filter.operator()) {
      case EXISTS -> exists(where, keyParam);
      case NOT_EXISTS -> notExists(where, keyParam);
      case EQ -> eq(where, params, filter, index, keyParam);
      case CONTAINS -> contains(where, params, filter, index, keyParam);
      case IN -> in(where, params, filter, index, keyParam);
      case GTE -> range(where, params, filter, index, keyParam, ">=");
      case LTE -> range(where, params, filter, index, keyParam, "<=");
      case BETWEEN -> between(where, params, filter, index, keyParam);
      case CONTAINS_ANY -> containsAny(where, params, filter, index, keyParam);
      case CONTAINS_ALL -> containsAll(where, params, filter, index, keyParam);
    }
  }

  private void exists(StringBuilder where, String keyParam) {
    where.append(" and custom_data_json ? :").append(keyParam).append(' ');
  }

  private void notExists(StringBuilder where, String keyParam) {
    where.append(" and not (custom_data_json ? :").append(keyParam).append(") ");
  }

  private void eq(
      StringBuilder where,
      Map<String, Object> params,
      RecordDynamicFilter filter,
      int index,
      String keyParam) {
    String valueParam = "dfValue" + index;
    params.put(valueParam, String.valueOf(filter.value()));

    switch (filter.fieldType()) {
      case NUMBER ->
          where.append(" and (custom_data_json ->> :")
              .append(keyParam)
              .append(")::numeric = cast(:")
              .append(valueParam)
              .append(" as numeric) ");
      case DATE ->
          where.append(" and (custom_data_json ->> :")
              .append(keyParam)
              .append(")::date = cast(:")
              .append(valueParam)
              .append(" as date) ");
      case DATETIME ->
          where.append(" and (custom_data_json ->> :")
              .append(keyParam)
              .append(")::timestamptz = cast(:")
              .append(valueParam)
              .append(" as timestamptz) ");
      case BOOLEAN ->
          where.append(" and custom_data_json ->> :")
              .append(keyParam)
              .append(" = :")
              .append(valueParam)
              .append(' ');
      case TEXT, TEXTAREA, SELECT, USER ->
          where.append(" and custom_data_json ->> :")
              .append(keyParam)
              .append(" = :")
              .append(valueParam)
              .append(' ');
      case MULTI_SELECT -> containsAll(where, params, filter, index, keyParam);
    }
  }

  private void contains(
      StringBuilder where,
      Map<String, Object> params,
      RecordDynamicFilter filter,
      int index,
      String keyParam) {
    String valueParam = "dfValue" + index;
    params.put(valueParam, "%" + escapeLike(String.valueOf(filter.value())) + "%");

    where.append(" and custom_data_json ->> :")
        .append(keyParam)
        .append(" ilike :")
        .append(valueParam)
        .append(" escape '\\' ");
  }

  private void in(
      StringBuilder where,
      Map<String, Object> params,
      RecordDynamicFilter filter,
      int index,
      String keyParam) {
    if (filter.fieldType() == FieldType.MULTI_SELECT) {
      containsAny(where, params, filter, index, keyParam);
      return;
    }

    String listParam = "dfList" + index;
    params.put(listParam, filter.values().stream().map(String::valueOf).toList());

    where.append(" and custom_data_json ->> :")
        .append(keyParam)
        .append(" in (:")
        .append(listParam)
        .append(") ");
  }

  private void range(
      StringBuilder where,
      Map<String, Object> params,
      RecordDynamicFilter filter,
      int index,
      String keyParam,
      String operator) {
    String valueParam = "dfValue" + index;
    params.put(valueParam, String.valueOf(filter.value()));

    where.append(" and ")
        .append(typedExpression(filter.fieldType(), keyParam))
        .append(' ')
        .append(operator)
        .append(' ')
        .append(typedParameter(filter.fieldType(), valueParam))
        .append(' ');
  }

  private void between(
      StringBuilder where,
      Map<String, Object> params,
      RecordDynamicFilter filter,
      int index,
      String keyParam) {
    if (filter.values() == null || filter.values().size() != 2) {
      throw new IllegalArgumentException("between requires two normalized values");
    }

    String fromParam = "dfFrom" + index;
    String toParam = "dfTo" + index;

    params.put(fromParam, String.valueOf(filter.values().get(0)));
    params.put(toParam, String.valueOf(filter.values().get(1)));

    where.append(" and ")
        .append(typedExpression(filter.fieldType(), keyParam))
        .append(" between ")
        .append(typedParameter(filter.fieldType(), fromParam))
        .append(" and ")
        .append(typedParameter(filter.fieldType(), toParam))
        .append(' ');
  }

  private void containsAny(
      StringBuilder where,
      Map<String, Object> params,
      RecordDynamicFilter filter,
      int index,
      String keyParam) {
    String listParam = "dfList" + index;
    params.put(listParam, filter.values().stream().map(String::valueOf).toList());

    where.append(" and exists (select 1 from jsonb_array_elements_text(")
        .append("coalesce(custom_data_json -> :")
        .append(keyParam)
        .append(", '[]'::jsonb)) as x(value) where x.value in (:")
        .append(listParam)
        .append(")) ");
  }

  private void containsAll(
      StringBuilder where,
      Map<String, Object> params,
      RecordDynamicFilter filter,
      int index,
      String keyParam) {
    String jsonParam = "dfJson" + index;
    params.put(jsonParam, toJson(filter.values()));

    where.append(" and coalesce(custom_data_json -> :")
        .append(keyParam)
        .append(", '[]'::jsonb) @> cast(:")
        .append(jsonParam)
        .append(" as jsonb) ");
  }

  private String typedExpression(FieldType fieldType, String keyParam) {
    return switch (fieldType) {
      case NUMBER -> "(custom_data_json ->> :" + keyParam + ")::numeric";
      case DATE -> "(custom_data_json ->> :" + keyParam + ")::date";
      case DATETIME -> "(custom_data_json ->> :" + keyParam + ")::timestamptz";
      default ->
          throw new IllegalArgumentException(
              "range operator is not supported for " + fieldType.value());
    };
  }

  private String typedParameter(FieldType fieldType, String valueParam) {
    return switch (fieldType) {
      case NUMBER -> "cast(:" + valueParam + " as numeric)";
      case DATE -> "cast(:" + valueParam + " as date)";
      case DATETIME -> "cast(:" + valueParam + " as timestamptz)";
      default ->
          throw new IllegalArgumentException(
              "range operator is not supported for " + fieldType.value());
    };
  }

  private String toJson(List<Object> values) {
    try {
      return objectMapper.writeValueAsString(values);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("failed to serialize dynamic filter values", ex);
    }
  }

  private String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
