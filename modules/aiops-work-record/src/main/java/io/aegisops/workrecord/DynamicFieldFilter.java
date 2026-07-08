package io.aegisops.workrecord;

import jakarta.validation.constraints.NotBlank;

/**
 * 动态字段筛选条件。
 *
 * <p>用于列表查询和导出的自定义字段筛选。operator 与 fieldType 的兼容性由
 * {@link WorkRecordFilterValidator} 校验。
 *
 * @param fieldCode 字段编码，必须存在于 wr_template_field 且 filterable=true
 * @param operator 筛选操作符：eq, in, contains, gte, lte, between, exists
 * @param value 单值（用于 eq, contains, gte, lte）
 * @param values 多值（用于 in）
 */
public record DynamicFieldFilter(
    @NotBlank(message = "fieldCode is required") String fieldCode,
    @NotBlank(message = "operator is required") String operator,
    Object value,
    Object values) {}
