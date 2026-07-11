package io.aegisops.workrecord.infrastructure.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.command.DynamicFilterOperator;
import io.aegisops.workrecord.application.command.RecordDynamicFilter;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.rule.FieldCodeRules;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * JSONB 动态筛选 SQL 构建器。
 *
 * <p>所有生成的 SQL 片段使用 NamedParameterJdbcTemplate 参数化查询，JSONB key/value 均通过参数传入，绝不直接拼接用户输入的 fieldCode
 * 或 value。
 *
 * <p>安全特性：
 *
 * <ul>
 *   <li>使用 {@code jsonb_extract_path_text()} + {@code cast(:key as text)} 避免 key 参数与 JDBC
 *       placeholder 冲突
 *   <li>使用 {@code jsonb_exists(..., cast(... as text))} 替代 {@code ?} 操作符，避免与 JDBC placeholder 歧义
 *   <li>number/date/datetime 通过 {@code work_record.try_*} 安全转换函数容忍历史脏数据
 *   <li>通过 {@code jsonb_typeof()} 先检查 JSON 类型再比较，避免 cast 报错
 *   <li>使用 {@code coalesce} 处理缺失字段
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
      StringBuilder where, Map<String, Object> params, List<RecordDynamicFilter> filters) {
    if (filters == null || filters.isEmpty()) {
      return;
    }

    for (int index = 0; index < filters.size(); index++) {
      appendFilter(where, params, filters.get(index), index);
    }
  }

  private void appendFilter(
      StringBuilder where, Map<String, Object> params, RecordDynamicFilter filter, int index) {
    if (filter == null) {
      throw new IllegalArgumentException("normalized filter must not be null");
    }

    FieldCodeRules.validate(filter.fieldCode());

    if (filter.fieldType() == null) {
      throw new IllegalArgumentException(
          "normalized filter fieldType is required: " + filter.fieldCode());
    }

    if (filter.operator() == null) {
      throw new IllegalArgumentException(
          "normalized filter operator is required: " + filter.fieldCode());
    }

    requireCompatible(filter.fieldType(), filter.operator());

    String keyParam = "dfKey" + index;
    params.put(keyParam, filter.fieldCode());

    switch (filter.operator()) {
      case EXISTS -> appendExists(where, keyParam, false);
      case NOT_EXISTS -> appendExists(where, keyParam, true);
      case EQ -> appendEq(where, params, filter, index, keyParam);
      case CONTAINS -> appendContains(where, params, filter, index, keyParam);
      case IN -> appendIn(where, params, filter, index, keyParam);
      case GTE -> appendRange(where, params, filter, index, keyParam, ">=");
      case LTE -> appendRange(where, params, filter, index, keyParam, "<=");
      case BETWEEN -> appendBetween(where, params, filter, index, keyParam);
      case CONTAINS_ANY -> appendContainsAny(where, params, filter, index, keyParam);
      case CONTAINS_ALL -> appendContainsAll(where, params, filter, index, keyParam);
    }
  }

  private void appendExists(StringBuilder where, String keyParam, boolean negate) {
    where.append(" and ");
    if (negate) {
      where.append("not ");
    }
    // Use jsonb_exists() with explicit cast to avoid ambiguity with JDBC ? placeholder.
    // The JSONB ? operator checks if a key exists at the top level of the JSON object.
    where.append("jsonb_exists(custom_data_json, cast(:").append(keyParam).append(" as text)) ");
  }

  private void appendEq(
      StringBuilder where,
      Map<String, Object> params,
      RecordDynamicFilter filter,
      int index,
      String keyParam) {
    String valueParam = "dfValue" + index;
    params.put(valueParam, String.valueOf(filter.value()));

    switch (filter.fieldType()) {
      case NUMBER -> {
        where
            .append(" and jsonb_typeof(")
            .append(jsonExpr(keyParam))
            .append(") = 'number' and work_record.try_numeric(")
            .append(textExpr(keyParam))
            .append(") = cast(:")
            .append(valueParam)
            .append(" as numeric) ");
      }
      case DATE -> {
        where
            .append(" and jsonb_typeof(")
            .append(jsonExpr(keyParam))
            .append(") = 'string' and work_record.try_date(")
            .append(textExpr(keyParam))
            .append(") = cast(:")
            .append(valueParam)
            .append(" as date) ");
      }
      case DATETIME -> {
        where
            .append(" and jsonb_typeof(")
            .append(jsonExpr(keyParam))
            .append(") = 'string' and work_record.")
            .append(safeFunction(FieldType.DATETIME))
            .append('(')
            .append(textExpr(keyParam))
            .append(") = cast(:")
            .append(valueParam)
            .append(" as timestamptz) ");
      }
      case BOOLEAN -> {
        where
            .append(" and jsonb_typeof(")
            .append(jsonExpr(keyParam))
            .append(") = 'boolean' and ")
            .append(textExpr(keyParam))
            .append(" = :")
            .append(valueParam)
            .append(' ');
      }
      case TEXT, TEXTAREA, SELECT, USER -> {
        where
            .append(" and jsonb_typeof(")
            .append(jsonExpr(keyParam))
            .append(") = 'string' and ")
            .append(textExpr(keyParam))
            .append(" = :")
            .append(valueParam)
            .append(' ');
      }
      case MULTI_SELECT ->
          throw new IllegalArgumentException(
              "eq is not supported for multi_select; use contains_any or contains_all");
    }
  }

  private void appendContains(
      StringBuilder where,
      Map<String, Object> params,
      RecordDynamicFilter filter,
      int index,
      String keyParam) {
    String valueParam = "dfValue" + index;
    params.put(valueParam, "%" + escapeLike(String.valueOf(filter.value())) + "%");

    where
        .append(" and jsonb_typeof(")
        .append(jsonExpr(keyParam))
        .append(") = 'string' and ")
        .append(textExpr(keyParam))
        .append(" ilike :")
        .append(valueParam)
        .append(" escape '\\' ");
  }

  private void appendIn(
      StringBuilder where,
      Map<String, Object> params,
      RecordDynamicFilter filter,
      int index,
      String keyParam) {
    if (filter.fieldType() == FieldType.MULTI_SELECT) {
      appendContainsAny(where, params, filter, index, keyParam);
      return;
    }

    requireValues(filter);

    String listParam = "dfList" + index;
    params.put(listParam, filter.values().stream().map(item -> String.valueOf(item)).toList());

    where
        .append(" and jsonb_typeof(")
        .append(jsonExpr(keyParam))
        .append(") = 'string' and ")
        .append(textExpr(keyParam))
        .append(" in (:")
        .append(listParam)
        .append(") ");
  }

  private void appendRange(
      StringBuilder where,
      Map<String, Object> params,
      RecordDynamicFilter filter,
      int index,
      String keyParam,
      String operator) {
    String valueParam = "dfValue" + index;
    params.put(valueParam, String.valueOf(filter.value()));

    String function = safeFunction(filter.fieldType());
    String jsonType = isNumericType(filter.fieldType()) ? "number" : "string";

    where
        .append(" and jsonb_typeof(")
        .append(jsonExpr(keyParam))
        .append(") = '")
        .append(jsonType)
        .append("' and work_record.")
        .append(function)
        .append('(')
        .append(textExpr(keyParam))
        .append(") ")
        .append(operator)
        .append(" cast(:")
        .append(valueParam)
        .append(" as ")
        .append(sqlType(filter.fieldType()))
        .append(") ");
  }

  private void appendBetween(
      StringBuilder where,
      Map<String, Object> params,
      RecordDynamicFilter filter,
      int index,
      String keyParam) {
    requireValues(filter);

    if (filter.values().size() != 2) {
      throw new IllegalArgumentException("between requires exactly two normalized values");
    }

    String fromParam = "dfFrom" + index;
    String toParam = "dfTo" + index;

    params.put(fromParam, String.valueOf(filter.values().get(0)));
    params.put(toParam, String.valueOf(filter.values().get(1)));

    String jsonType = isNumericType(filter.fieldType()) ? "number" : "string";
    String function = safeFunction(filter.fieldType());

    where
        .append(" and jsonb_typeof(")
        .append(jsonExpr(keyParam))
        .append(") = '")
        .append(jsonType)
        .append("' and work_record.")
        .append(function)
        .append('(')
        .append(textExpr(keyParam))
        .append(") between cast(:")
        .append(fromParam)
        .append(" as ")
        .append(sqlType(filter.fieldType()))
        .append(") and cast(:")
        .append(toParam)
        .append(" as ")
        .append(sqlType(filter.fieldType()))
        .append(") ");
  }

  private void appendContainsAny(
      StringBuilder where,
      Map<String, Object> params,
      RecordDynamicFilter filter,
      int index,
      String keyParam) {
    requireValues(filter);

    // Wrap the OR-combined containment branches in a single AND group so the
    // generated fragment composes cleanly with neighbouring WHERE clauses:
    //   ... and ( <branch 1> or <branch 2> ... )
    where.append(" and (");

    // For each value, build a separate containment check.
    // Using @> with individual key-value pairs avoids jsonb_array_elements_text
    // failing when the field is a scalar instead of an array.
    for (int valueIndex = 0; valueIndex < filter.values().size(); valueIndex++) {
      if (valueIndex > 0) {
        where.append(" or ");
      }

      String valueParam = "dfAny" + index + "_" + valueIndex;
      params.put(valueParam, String.valueOf(filter.values().get(valueIndex)));

      where
          .append("(jsonb_typeof(")
          .append(jsonExpr(keyParam))
          .append(") = 'array' and custom_data_json @> ")
          .append("jsonb_build_object(")
          .append("cast(:")
          .append(keyParam)
          .append(" as text), ")
          .append("jsonb_build_array(to_jsonb(cast(:")
          .append(valueParam)
          // cast + to_jsonb + array + object + 当前分支
          .append(" as text)))))");
    }

    // 关闭整个 OR 分组
    where.append(") ");
  }

  private void appendContainsAll(
      StringBuilder where,
      Map<String, Object> params,
      RecordDynamicFilter filter,
      int index,
      String keyParam) {
    requireValues(filter);

    String jsonParam = "dfJson" + index;
    params.put(jsonParam, toJson(filter.values()));

    // Use coalesce to handle missing fields gracefully.
    // The @> operator checks if the array on the left contains all elements of the right.
    where
        .append(" and (jsonb_typeof(")
        .append(jsonExpr(keyParam))
        .append(") = 'array' and coalesce(custom_data_json -> cast(:")
        .append(keyParam)
        .append(" as text), '[]'::jsonb) @> cast(:")
        .append(jsonParam)
        .append(" as jsonb)) ");
  }

  private String safeFunction(FieldType type) {
    return switch (type) {
      case NUMBER -> "try_numeric";
      case DATE -> "try_date";
      case DATETIME -> "try_timestamptz";
      default ->
          throw new IllegalArgumentException("range operator is not supported for " + type.value());
    };
  }

  private String sqlType(FieldType type) {
    return switch (type) {
      case NUMBER -> "numeric";
      case DATE -> "date";
      case DATETIME -> "timestamptz";
      default ->
          throw new IllegalArgumentException("range operator is not supported for " + type.value());
    };
  }

  private boolean isNumericType(FieldType type) {
    return type == FieldType.NUMBER;
  }

  /** Extract JSON value by key (returns the JSON value, not text). */
  private String jsonExpr(String keyParam) {
    return "jsonb_extract_path(custom_data_json, cast(:" + keyParam + " as text))";
  }

  /** Extract JSON value as text. */
  private String textExpr(String keyParam) {
    return "jsonb_extract_path_text(custom_data_json, cast(:" + keyParam + " as text))";
  }

  private void requireValues(RecordDynamicFilter filter) {
    if (filter.values() == null || filter.values().isEmpty()) {
      throw new IllegalArgumentException(filter.operator().value() + " requires normalized values");
    }
  }

  private void requireCompatible(FieldType type, DynamicFilterOperator operator) {
    EnumSet<DynamicFilterOperator> allowed =
        switch (type) {
          case TEXT, TEXTAREA ->
              EnumSet.of(
                  DynamicFilterOperator.CONTAINS,
                  DynamicFilterOperator.EQ,
                  DynamicFilterOperator.EXISTS,
                  DynamicFilterOperator.NOT_EXISTS);
          case NUMBER, DATE, DATETIME ->
              EnumSet.of(
                  DynamicFilterOperator.EQ,
                  DynamicFilterOperator.GTE,
                  DynamicFilterOperator.LTE,
                  DynamicFilterOperator.BETWEEN,
                  DynamicFilterOperator.EXISTS,
                  DynamicFilterOperator.NOT_EXISTS);
          case SELECT ->
              EnumSet.of(
                  DynamicFilterOperator.EQ,
                  DynamicFilterOperator.IN,
                  DynamicFilterOperator.EXISTS,
                  DynamicFilterOperator.NOT_EXISTS);
          case MULTI_SELECT ->
              EnumSet.of(
                  DynamicFilterOperator.IN,
                  DynamicFilterOperator.CONTAINS_ANY,
                  DynamicFilterOperator.CONTAINS_ALL,
                  DynamicFilterOperator.EXISTS,
                  DynamicFilterOperator.NOT_EXISTS);
          case BOOLEAN ->
              EnumSet.of(
                  DynamicFilterOperator.EQ,
                  DynamicFilterOperator.EXISTS,
                  DynamicFilterOperator.NOT_EXISTS);
          case USER ->
              EnumSet.of(
                  DynamicFilterOperator.EQ,
                  DynamicFilterOperator.IN,
                  DynamicFilterOperator.EXISTS,
                  DynamicFilterOperator.NOT_EXISTS);
        };

    if (!allowed.contains(operator)) {
      throw new IllegalArgumentException(
          "operator " + operator.value() + " is incompatible with " + type.value());
    }
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
