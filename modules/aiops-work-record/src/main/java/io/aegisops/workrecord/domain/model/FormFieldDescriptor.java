package io.aegisops.workrecord.domain.model;

public record FormFieldDescriptor(
    String fieldName,
    String fieldCode,
    FieldType fieldType,
    boolean required,
    OptionSource optionSource,
    String dictCode,
    String optionsJson,
    String schemaPath,
    boolean listVisible,
    boolean filterable,
    boolean exportable,
    boolean statistical,
    int sortOrder) {}
