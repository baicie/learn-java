package io.aegisops.workrecord.application.service;

import io.aegisops.workrecord.application.schema.WorkRecordSchemaDocument;
import io.aegisops.workrecord.application.schema.WorkRecordSchemaNormalizer;
import io.aegisops.workrecord.application.schema.WorkRecordSchemaParser;
import io.aegisops.workrecord.application.schema.WorkRecordSchemaValidator;
import io.aegisops.workrecord.domain.model.FormFieldDescriptor;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordSchemaService {
  private final WorkRecordSchemaParser parser;
  private final WorkRecordSchemaValidator validator;
  private final WorkRecordSchemaNormalizer normalizer;

  public WorkRecordSchemaService(
      WorkRecordSchemaParser parser,
      WorkRecordSchemaValidator validator,
      WorkRecordSchemaNormalizer normalizer) {
    this.parser = parser;
    this.validator = validator;
    this.normalizer = normalizer;
  }

  public WorkRecordSchemaDocument prepareForPublish(String schemaJson, String designerJson) {
    List<FormFieldDescriptor> fields = parser.parse(schemaJson);
    validator.validateFieldList(fields);

    String normalizedSchema = normalizer.normalizeSchema(schemaJson);
    String normalizedDesigner = normalizer.normalizeDesignerJson(designerJson);
    String fieldIndexJson = normalizer.fieldIndexJson(fields);

    return new WorkRecordSchemaDocument(
        parser.schemaVersion(normalizedSchema),
        normalizedSchema,
        normalizedDesigner,
        fields,
        fieldIndexJson);
  }

  public String normalizeObject(String json, String fieldName) {
    if ("designerJson".equals(fieldName) || "draftDesignerJson".equals(fieldName)) {
      return normalizer.normalizeDesignerJson(json);
    }
    return prepareForPublish(json, "{}").normalizedSchemaJson();
  }

  public List<FormFieldDescriptor> extractFields(String schemaJson) {
    return parser.parse(schemaJson);
  }

  public String toFieldIndexJson(List<FormFieldDescriptor> descriptors) {
    validator.validateFieldList(descriptors);
    return normalizer.fieldIndexJson(descriptors);
  }
}