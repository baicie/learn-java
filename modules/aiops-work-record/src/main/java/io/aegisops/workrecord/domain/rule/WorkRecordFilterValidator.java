package io.aegisops.workrecord.domain.rule;

import io.aegisops.workrecord.domain.model.DynamicFieldFilter;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 动态字段筛选校验器。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>校验 fieldCode 存在于模板字段定义
 *   <li>校验 field.filterable=true
 *   <li>校验 operator 与 fieldType 兼容
 *   <li>校验 value 类型与字段类型兼容
 * </ul>
 */
public final class WorkRecordFilterValidator {

  private static final Set<String> TEXT_OPS = Set.of("contains", "eq", "exists");
  private static final Set<String> NUMBER_OPS = Set.of("eq", "gte", "lte", "between", "exists");
  private static final Set<String> DATE_OPS = Set.of("eq", "gte", "lte", "between", "exists");
  private static final Set<String> SELECT_OPS = Set.of("eq", "in", "exists");
  private static final Set<String> MULTI_SELECT_OPS = Set.of("in", "exists");
  private static final Set<String> BOOL_OPS = Set.of("eq", "exists");
  private static final Set<String> USER_OPS = Set.of("eq", "in", "exists");

  private WorkRecordFilterValidator() {}

  /**
   * 校验动态字段筛选列表。
   *
   * @param templateFields 当前模板下所有字段
   * @param filters 动态字段筛选列表
   * @throws IllegalArgumentException 校验失败
   */
  public static void validate(
      List<WorkRecordField> templateFields, List<DynamicFieldFilter> filters) {
    if (filters == null || filters.isEmpty()) {
      return;
    }

    Map<String, WorkRecordField> fieldByCode =
        templateFields.stream()
            .filter(field -> field.enabled())
            .collect(Collectors.toMap(WorkRecordField::fieldCode, f -> f, (a, b) -> a));

    for (DynamicFieldFilter filter : filters) {
      validateSingle(fieldByCode, filter);
    }
  }

  private static void validateSingle(
      Map<String, WorkRecordField> fieldByCode, DynamicFieldFilter filter) {
    String fieldCode = filter.fieldCode();
    if (fieldCode == null || fieldCode.isBlank()) {
      throw new IllegalArgumentException("fieldCode is required in dynamic filter");
    }

    WorkRecordField field = fieldByCode.get(fieldCode);
    if (field == null) {
      throw new IllegalArgumentException(
          "fieldCode '" + fieldCode + "' is not defined in template or is disabled");
    }

    if (!field.filterable()) {
      throw new IllegalArgumentException("fieldCode '" + fieldCode + "' is not filterable");
    }

    String operator = filter.operator();
    if (operator == null || operator.isBlank()) {
      throw new IllegalArgumentException("operator is required for field '" + fieldCode + "'");
    }

    Set<String> allowedOps = allowedOperators(field.fieldType().value());
    if (!allowedOps.contains(operator)) {
      throw new IllegalArgumentException(
          "operator '"
              + operator
              + "' is not supported for fieldType '"
              + field.fieldType()
              + "' on field '"
              + fieldCode
              + "'");
    }

    validateValueType(field, filter);
  }

  private static void validateValueType(WorkRecordField field, DynamicFieldFilter filter) {
    String type = field.fieldType().value();
    String op = filter.operator();

    if ("exists".equals(op)) {
      return; // exists 不需要 value
    }

    if ("in".equals(op)) {
      if (filter.values() == null) {
        throw new IllegalArgumentException(
            "field '" + filter.fieldCode() + "' with operator 'in' requires 'values' array");
      }
      return;
    }

    if ("between".equals(op)) {
      @SuppressWarnings("unchecked")
      List<Object> betweenValues = (List<Object>) filter.values();
      if (betweenValues == null || betweenValues.size() < 2) {
        throw new IllegalArgumentException(
            "field '" + filter.fieldCode() + "' with operator 'between' requires two values");
      }
      return;
    }

    if (filter.value() == null) {
      throw new IllegalArgumentException(
          "field '" + filter.fieldCode() + "' with operator '" + op + "' requires 'value'");
    }

    Object val = filter.value();
    switch (type) {
      case "number":
        if (!(val instanceof Number)) {
          throw new IllegalArgumentException(
              "field '" + filter.fieldCode() + "' expects number value");
        }
        break;
      case "switch":
      case "boolean":
        if (!(val instanceof Boolean)) {
          throw new IllegalArgumentException(
              "field '" + filter.fieldCode() + "' expects boolean value");
        }
        break;
      case "date":
      case "datetime":
        if (!(val instanceof String)) {
          throw new IllegalArgumentException(
              "field '" + filter.fieldCode() + "' expects string value");
        }
        break;
      case "select":
      case "text":
      case "textarea":
      case "user":
        if (!(val instanceof String)) {
          throw new IllegalArgumentException(
              "field '" + filter.fieldCode() + "' expects string value");
        }
        break;
      default:
        break;
    }
  }

  private static Set<String> allowedOperators(String fieldType) {
    return switch (fieldType) {
      case "text", "textarea" -> TEXT_OPS;
      case "number" -> NUMBER_OPS;
      case "date", "datetime" -> DATE_OPS;
      case "select" -> SELECT_OPS;
      case "multi_select" -> MULTI_SELECT_OPS;
      case "switch", "boolean" -> BOOL_OPS;
      case "user" -> USER_OPS;
      default -> TEXT_OPS;
    };
  }
}
