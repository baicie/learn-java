package io.aegisops.workrecord.application.schema;

import io.aegisops.workrecord.domain.model.FormFieldDescriptor;
import java.util.List;

public record WorkRecordSchemaDocument(
    int schemaVersion,
    String normalizedSchemaJson,
    String normalizedDesignerJson,
    List<FormFieldDescriptor> fields,
    String fieldIndexJson) {}
