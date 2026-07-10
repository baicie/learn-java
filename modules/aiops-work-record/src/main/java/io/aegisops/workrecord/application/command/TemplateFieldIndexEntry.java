package io.aegisops.workrecord.application.command;

import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.FormFieldDescriptor;
import io.aegisops.workrecord.domain.model.OptionSource;
import io.aegisops.workrecord.domain.model.WorkRecordField;

public record TemplateFieldIndexEntry(
    String fieldName,
    String fieldCode,
    FieldType fieldType,
    boolean required,
    String defaultValue,
    OptionSource optionSource,
    String dictCode,
    String optionsJson,
    String schemaPath,
    boolean listVisible,
    boolean filterable,
    boolean exportable,
    boolean statistical,
    int sortOrder,
    boolean enabled) {

  public static TemplateFieldIndexEntry enabled(FormFieldDescriptor field) {
    return new TemplateFieldIndexEntry(
        field.fieldName(),
        field.fieldCode(),
        field.fieldType(),
        field.required(),
        null,
        field.optionSource(),
        field.dictCode(),
        field.optionsJson(),
        field.schemaPath(),
        field.listVisible(),
        field.filterable(),
        field.exportable(),
        field.statistical(),
        field.sortOrder(),
        true);
  }

  public static TemplateFieldIndexEntry disabledFrom(WorkRecordField field) {
    return new TemplateFieldIndexEntry(
        field.fieldName(),
        field.fieldCode(),
        field.fieldType(),
        field.required(),
        field.defaultValue(),
        field.optionSource(),
        field.dictCode(),
        field.optionsJson(),
        field.schemaPath(),
        field.listVisible(),
        field.filterable(),
        field.exportable(),
        field.statistical(),
        field.sortOrder(),
        false);
  }
}
