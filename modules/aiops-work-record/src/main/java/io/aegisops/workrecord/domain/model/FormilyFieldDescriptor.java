package io.aegisops.workrecord.domain.model;

/**
 * Formily schema 字段描述符。
 *
 * <p>由 {@link io.aegisops.workrecord.application.WorkRecordSchemaService#extractFields(String)} 从
 * Formily-compatible schema 中抽取， 用于同步写入 {@code wr_template_field} 字段索引表。
 *
 * @param fieldCode 字段编码（取自 properties key 或 x-work-record-field-code）
 * @param fieldName 展示标题
 * @param fieldType 归一后的字段类型（见 FIELD_TYPES）
 * @param optionSource 选项来源：{@code static} 或 {@code dict}
 * @param dictCode 字典编码（optionSource=static 时为 null）
 * @param listVisible 是否在列表中展示
 * @param filterable 是否支持筛选
 * @param statistical 是否支持统计
 * @param schemaPath 字段在 schema 中的路径（如 {@code .properties.priority}）
 */
public record FormilyFieldDescriptor(
    String fieldCode,
    String fieldName,
    String fieldType,
    String optionSource,
    String dictCode,
    boolean listVisible,
    boolean filterable,
    boolean statistical,
    String schemaPath) {}
