package io.aegisops.workrecord.application.schema;

import static io.aegisops.workrecord.application.schema.WorkRecordSchemaContract.ROOT_SCHEMA_VERSION_KEY;
import static io.aegisops.workrecord.application.schema.WorkRecordSchemaContract.SUPPORTED_FIELD_TYPES;
import static io.aegisops.workrecord.application.schema.WorkRecordSchemaContract.SUPPORTED_OPTION_SOURCES;

import com.fasterxml.jackson.databind.JsonNode;
import io.aegisops.workrecord.domain.model.FormFieldDescriptor;
import io.aegisops.workrecord.domain.model.OptionSource;
import io.aegisops.workrecord.domain.rule.FieldCodeRules;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class WorkRecordSchemaValidator {

  public void validateRoot(JsonNode root) {
    if (root == null || !root.isObject()) {
      throw new IllegalArgumentException("schemaJson must be object");
    }

    rejectFlatProtocol(root, "$");

    if (root.has(ROOT_SCHEMA_VERSION_KEY)) {
      int version = root.path(ROOT_SCHEMA_VERSION_KEY).asInt();
      if (version != WorkRecordSchemaContract.CURRENT_SCHEMA_VERSION) {
        throw new IllegalArgumentException("unsupported schema version: " + version);
      }
    }

    JsonNode type = root.path("type");
    if (!type.isMissingNode() && !"object".equals(type.asText())) {
      throw new IllegalArgumentException("root schema type must be object");
    }
  }

  public void rejectFlatProtocol(JsonNode node, String path) {
    if (node == null || !node.isObject()) {
      return;
    }

    var names = node.fieldNames();
    while (names.hasNext()) {
      String key = names.next();
      if (WorkRecordSchemaContract.isFlatWorkRecordKey(key)) {
        throw new IllegalArgumentException(
            "flat work-record schema extension is forbidden at " + path + ": " + key);
      }
    }
  }

  public void validateFieldExtension(JsonNode ext, String schemaPath) {
    if (ext == null || !ext.isObject()) {
      throw new IllegalArgumentException("x-work-record must be object at " + schemaPath);
    }

    String fieldCode = requiredText(ext, "fieldCode", schemaPath);
    validateFieldCode(fieldCode);

    String fieldType = requiredText(ext, "fieldType", schemaPath);
    if (!SUPPORTED_FIELD_TYPES.contains(fieldType)) {
      throw new IllegalArgumentException(
          "unsupported fieldType at " + schemaPath + ": " + fieldType);
    }

    String optionSource = optionalText(ext, "optionSource", "static");
    if (!SUPPORTED_OPTION_SOURCES.contains(optionSource)) {
      throw new IllegalArgumentException(
          "unsupported optionSource at " + schemaPath + ": " + optionSource);
    }

    String dictCode = optionalText(ext, "dictCode", null);
    if ("dict".equals(optionSource) && (dictCode == null || dictCode.isBlank())) {
      throw new IllegalArgumentException("dictCode is required for dict field at " + schemaPath);
    }

    if (!"dict".equals(optionSource) && dictCode != null && !dictCode.isBlank()) {
      throw new IllegalArgumentException(
          "dictCode is only allowed when optionSource=dict at " + schemaPath);
    }
  }

  public void validateFieldList(List<FormFieldDescriptor> fields) {
    Set<String> seen = new HashSet<>();
    for (FormFieldDescriptor field : fields) {
      validateFieldCode(field.fieldCode());

      if (!seen.add(field.fieldCode())) {
        throw new IllegalArgumentException("duplicated fieldCode: " + field.fieldCode());
      }

      if (field.optionSource() == OptionSource.DICT
          && (field.dictCode() == null || field.dictCode().isBlank())) {
        throw new IllegalArgumentException("dictCode is required: " + field.fieldCode());
      }
    }
  }

  public void validateFieldCode(String fieldCode) {
    FieldCodeRules.validate(fieldCode);
  }

  private String requiredText(JsonNode node, String field, String path) {
    String value = optionalText(node, field, null);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required at " + path);
    }
    return value;
  }

  private String optionalText(JsonNode node, String field, String fallback) {
    JsonNode value = node.path(field);
    if (value.isMissingNode() || value.isNull()) {
      return fallback;
    }
    return value.asText();
  }
}
