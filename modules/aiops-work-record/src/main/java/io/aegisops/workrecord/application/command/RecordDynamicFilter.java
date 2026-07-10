package io.aegisops.workrecord.application.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.aegisops.workrecord.domain.model.FieldType;
import java.util.List;

/**
 * 动态筛选条件。
 *
 * <p>JSON 反序列化时接收原始 DTO（operator/fieldType 为 String），标准化后 由 {@link
 * io.aegisops.workrecord.application.service.WorkRecordDynamicFilterPolicyService} 转换为带 {@link
 * DynamicFilterOperator} enum 和 {@link FieldType} enum 的内部表示，并补全 operator/fieldType 字段。
 */
public record RecordDynamicFilter(
    String fieldCode,
    DynamicFilterOperator operator,
    FieldType fieldType,
    Object value,
    List<Object> values) {

  /**
   * JSON 反序列化构造器。
   *
   * <p>Jackson 会把 operator 字符串和 fieldType 字符串反序列化为对应 enum， 若无法匹配则抛异常（由 Controller 层捕获返回 400）。
   */
  @JsonCreator
  public RecordDynamicFilter(
      @JsonProperty("fieldCode") String fieldCode,
      @JsonProperty("operator") String operator,
      @JsonProperty("value") Object value,
      @JsonProperty("values") List<Object> values) {
    this(
        fieldCode,
        operator != null ? DynamicFilterOperator.from(operator) : null,
        null,
        value,
        values);
  }

  /**
   * 标准化工厂方法：构建已校验的内部表示。
   *
   * @param fieldCode 字段编码
   * @param operator 操作符（标准化后）
   * @param fieldType 字段类型（标准化后）
   * @param value 单值（用于 EQ/CONTAINS/GTE/LTE）
   * @param values 列表值（用于 IN/BETWEEN/CONTAINS_ANY/CONTAINS_ALL）
   */
  public static RecordDynamicFilter normalized(
      String fieldCode,
      DynamicFilterOperator operator,
      FieldType fieldType,
      Object value,
      List<Object> values) {
    return new RecordDynamicFilter(fieldCode, operator, fieldType, value, values);
  }

  /** 快捷方法：构建只有 fieldCode 和 operator 的原始对象（用于测试） */
  public static RecordDynamicFilter raw(String fieldCode, String operator, Object value) {
    return new RecordDynamicFilter(fieldCode, operator, value, null);
  }

  /** 判断是否为存在性操作符 */
  public boolean existenceOperator() {
    return operator == DynamicFilterOperator.EXISTS || operator == DynamicFilterOperator.NOT_EXISTS;
  }
}
