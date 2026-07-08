package io.aegisops.workrecord;

/** 更新字段请求；所有字段可选，仅非 null 时生效。 */
public record UpdateFieldRequest(
    String fieldName,
    Boolean required,
    String defaultValue,
    String optionSource,
    String dictCode,
    String optionsJson,
    Boolean listVisible,
    Boolean filterable,
    Boolean exportable,
    Boolean statistical,
    Integer sortOrder,
    Boolean enabled) {}
