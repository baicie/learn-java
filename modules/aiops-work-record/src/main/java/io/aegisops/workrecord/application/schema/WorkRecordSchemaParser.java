package io.aegisops.workrecord.application.schema;

import static io.aegisops.workrecord.application.schema.WorkRecordSchemaContract.FIELD_EXTENSION_KEY;
import static io.aegisops.workrecord.application.schema.WorkRecordSchemaContract.ROOT_SCHEMA_VERSION_KEY;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.FormFieldDescriptor;
import io.aegisops.workrecord.domain.model.OptionSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class WorkRecordSchemaParser {
  private final ObjectMapper objectMapper;
  private final WorkRecordSchemaValidator validator;

  public WorkRecordSchemaParser(ObjectMapper objectMapper, WorkRecordSchemaValidator validator) {
    this.objectMapper = objectMapper;
    this.validator = validator;
  }

  public List<FormFieldDescriptor> parse(String schemaJson) {
    try {
      JsonNode root =
          objectMapper.readTree(schemaJson == null || schemaJson.isBlank() ? "{}" : schemaJson);
      validator.validateRoot(root);

      List<FormFieldDescriptor> fields = new ArrayList<>();
      parseObject(root, ".properties", fields);

      validator.validateFieldList(fields);
      return List.copyOf(fields);
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("failed to parse work-record schema", ex);
    }
  }

  private void parseObject(
      JsonNode objectNode, String pathPrefix, List<FormFieldDescriptor> output) {
    JsonNode properties = objectNode.path("properties");
    if (!properties.isObject()) {
      return;
    }

    Set<String> requiredNames = readRequiredNames(objectNode);
    var names = properties.fieldNames();

    while (names.hasNext()) {
      String propertyName = names.next();
      JsonNode fieldNode = properties.get(propertyName);
      String schemaPath = pathPrefix + "." + propertyName;

      validator.rejectFlatProtocol(fieldNode, schemaPath);

      if (fieldNode.has(FIELD_EXTENSION_KEY)) {
        output.add(parseField(propertyName, fieldNode, schemaPath, requiredNames, output.size()));
      }

      if (fieldNode.path("properties").isObject()) {
        parseObject(fieldNode, schemaPath + ".properties", output);
      }
    }
  }

  private FormFieldDescriptor parseField(
      String propertyName,
      JsonNode fieldNode,
      String schemaPath,
      Set<String> requiredNames,
      int defaultSortOrder) {
    JsonNode ext = fieldNode.path(FIELD_EXTENSION_KEY);
    validator.validateFieldExtension(ext, schemaPath);

    String fieldCode = text(ext, "fieldCode");
    String fieldTypeValue = text(ext, "fieldType");
    String optionSourceValue = textOrDefault(ext, "optionSource", "static");
    String dictCode = nullableText(ext, "dictCode");

    FieldType fieldType = FieldType.from(fieldTypeValue);
    OptionSource optionSource = OptionSource.from(optionSourceValue);
    validateFieldRules(fieldNode, fieldType, optionSource, fieldCode);

    return new FormFieldDescriptor(
        textOrDefault(fieldNode, "title", fieldCode),
        fieldCode,
        fieldType,
        requiredNames.contains(propertyName) || ext.path("required").asBoolean(false),
        optionSource,
        dictCode,
        optionsJson(fieldNode),
        schemaPath,
        ext.path("columnSpan").asInt(2),
        validationJson(fieldNode),
        ext.path("listVisible").asBoolean(false),
        ext.path("filterable").asBoolean(false),
        ext.path("exportable").asBoolean(true),
        ext.path("statistical").asBoolean(false),
        ext.path("sortOrder").asInt(defaultSortOrder));
  }

  public int schemaVersion(String schemaJson) {
    try {
      JsonNode root =
          objectMapper.readTree(schemaJson == null || schemaJson.isBlank() ? "{}" : schemaJson);
      if (!root.has(ROOT_SCHEMA_VERSION_KEY)) {
        return WorkRecordSchemaContract.CURRENT_SCHEMA_VERSION;
      }
      return root.path(ROOT_SCHEMA_VERSION_KEY).asInt();
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid schema json", ex);
    }
  }

  private Set<String> readRequiredNames(JsonNode node) {
    Set<String> result = new HashSet<>();
    JsonNode required = node.path("required");
    if (!required.isArray()) {
      return result;
    }
    for (JsonNode item : required) {
      if (item.isTextual() && !item.asText().isBlank()) {
        result.add(item.asText());
      }
    }
    return result;
  }

  private String optionsJson(JsonNode fieldNode) {
    try {
      JsonNode options = fieldNode.path("enum");
      if (options.isArray()) {
        return objectMapper.writeValueAsString(options);
      }

      JsonNode dataSource = fieldNode.path("x-component-props").path("dataSource");
      if (dataSource.isArray()) {
        return objectMapper.writeValueAsString(dataSource);
      }

      return "[]";
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid field options", ex);
    }
  }

  private String validationJson(JsonNode fieldNode) {
    try {
      var rules = objectMapper.createObjectNode();
      copyRule(fieldNode, rules, "minLength");
      copyRule(fieldNode, rules, "maxLength");
      copyRule(fieldNode, rules, "pattern");
      copyRule(fieldNode, rules, "minimum");
      copyRule(fieldNode, rules, "maximum");
      return objectMapper.writeValueAsString(rules);
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid field validation rules", ex);
    }
  }

  private void validateFieldRules(
      JsonNode fieldNode, FieldType fieldType, OptionSource optionSource, String fieldCode) {
    boolean optionField = fieldType == FieldType.SELECT || fieldType == FieldType.MULTI_SELECT;
    if (optionField && optionSource == OptionSource.STATIC && !hasStaticOptions(fieldNode)) {
      throw new IllegalArgumentException("static options are required: " + fieldCode);
    }

    try {
      boolean textField = fieldType == FieldType.TEXT || fieldType == FieldType.TEXTAREA;
      boolean numberField = fieldType == FieldType.NUMBER;
      if (!textField
          && (fieldNode.has("minLength")
              || fieldNode.has("maxLength")
              || fieldNode.has("pattern"))) {
        throw new IllegalArgumentException("text validation rules require a text field");
      }
      if (!numberField && (fieldNode.has("minimum") || fieldNode.has("maximum"))) {
        throw new IllegalArgumentException("number validation rules require a number field");
      }
      validateLengthRule(fieldNode, "minLength");
      validateLengthRule(fieldNode, "maxLength");
      if (fieldNode.has("minLength")
          && fieldNode.has("maxLength")
          && fieldNode.path("minLength").asInt() > fieldNode.path("maxLength").asInt()) {
        throw new IllegalArgumentException("minLength exceeds maxLength");
      }
      if (fieldNode.has("pattern")) {
        if (!fieldNode.path("pattern").isTextual()) {
          throw new IllegalArgumentException("pattern must be string");
        }
        Pattern.compile(fieldNode.path("pattern").asText());
      }
      validateNumberRule(fieldNode, "minimum");
      validateNumberRule(fieldNode, "maximum");
      if (fieldNode.has("minimum")
          && fieldNode.has("maximum")
          && fieldNode.path("minimum").asDouble() > fieldNode.path("maximum").asDouble()) {
        throw new IllegalArgumentException("minimum exceeds maximum");
      }
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("invalid field validation rules: " + fieldCode, ex);
    }
  }

  private boolean hasStaticOptions(JsonNode fieldNode) {
    JsonNode values = fieldNode.path("enum");
    if (values.isArray() && !values.isEmpty()) return true;
    JsonNode dataSource = fieldNode.path("x-component-props").path("dataSource");
    return dataSource.isArray() && !dataSource.isEmpty();
  }

  private void validateLengthRule(JsonNode fieldNode, String name) {
    if (!fieldNode.has(name)) return;
    JsonNode value = fieldNode.path(name);
    if (!value.isIntegralNumber() || value.asInt() < 0) {
      throw new IllegalArgumentException(name + " must be a non-negative integer");
    }
  }

  private void validateNumberRule(JsonNode fieldNode, String name) {
    if (fieldNode.has(name) && !fieldNode.path(name).isNumber()) {
      throw new IllegalArgumentException(name + " must be a number");
    }
  }

  private void copyRule(
      JsonNode source, com.fasterxml.jackson.databind.node.ObjectNode target, String name) {
    JsonNode value = source.get(name);
    if (value != null && !value.isNull()) {
      target.set(name, value);
    }
  }

  private String text(JsonNode node, String field) {
    String value = nullableText(node, field);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value;
  }

  private String textOrDefault(JsonNode node, String field, String fallback) {
    String value = nullableText(node, field);
    return value == null || value.isBlank() ? fallback : value;
  }

  private String nullableText(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() ? null : value.asText();
  }
}
