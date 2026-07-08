package io.aegisops.workrecord;

public record CreateFieldRequest(
    String fieldName,
    String fieldCode,
    String fieldType,
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
    Boolean enabled,
    String schemaPath) {}
