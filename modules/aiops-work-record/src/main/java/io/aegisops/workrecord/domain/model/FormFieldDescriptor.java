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
    int columnSpan,
    String validationJson,
    boolean listVisible,
    boolean filterable,
    boolean exportable,
    boolean statistical,
    int sortOrder) {
  public FormFieldDescriptor(
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
      int sortOrder) {
    this(
        fieldName,
        fieldCode,
        fieldType,
        required,
        optionSource,
        dictCode,
        optionsJson,
        schemaPath,
        2,
        "{}",
        listVisible,
        filterable,
        exportable,
        statistical,
        sortOrder);
  }
}
