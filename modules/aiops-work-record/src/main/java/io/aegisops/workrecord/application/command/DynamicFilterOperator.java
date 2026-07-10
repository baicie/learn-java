package io.aegisops.workrecord.application.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * 动态筛选操作符枚举。 支持以下操作符：
 *
 * <ul>
 *   <li>{@link #EQ} - 等于
 *   <li>{@link #CONTAINS} - 文本包含（LIKE %value%）
 *   <li>{@link #IN} - 在列表中
 *   <li>{@link #GTE} - 大于等于
 *   <li>{@link #LTE} - 小于等于
 *   <li>{@link #BETWEEN} - 范围（需两个值）
 *   <li>{@link #CONTAINS_ANY} - 数组包含任意一项
 *   <li>{@link #CONTAINS_ALL} - 数组包含所有项
 *   <li>{@link #EXISTS} - 字段存在
 *   <li>{@link #NOT_EXISTS} - 字段不存在
 * </ul>
 */
public enum DynamicFilterOperator {
  /** 文本包含 */
  CONTAINS("contains"),
  /** 等于 */
  EQ("eq"),
  /** 在列表中 */
  IN("in"),
  /** 大于等于 */
  GTE("gte"),
  /** 小于等于 */
  LTE("lte"),
  /** 范围（需两个值） */
  BETWEEN("between"),
  /** 数组包含任意一项 */
  CONTAINS_ANY("contains_any"),
  /** 数组包含所有项 */
  CONTAINS_ALL("contains_all"),
  /** 字段存在 */
  EXISTS("exists"),
  /** 字段不存在 */
  NOT_EXISTS("not_exists");

  private final String value;

  DynamicFilterOperator(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }

  @JsonCreator
  public static DynamicFilterOperator from(String raw) {
    if (raw == null || raw.isBlank()) {
      return EQ;
    }

    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    for (DynamicFilterOperator operator : values()) {
      if (operator.value.equals(normalized) || operator.name().equalsIgnoreCase(normalized)) {
        return operator;
      }
    }

    throw new IllegalArgumentException("unsupported dynamic filter operator: " + raw);
  }
}
