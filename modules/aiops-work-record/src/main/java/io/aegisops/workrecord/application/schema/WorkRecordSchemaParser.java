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

    return new FormFieldDescriptor(
        textOrDefault(fieldNode, "title", fieldCode),
        fieldCode,
        fieldType,
        requiredNames.contains(propertyName) || ext.path("required").asBoolean(false),
        optionSource,
        dictCode,
        optionsJson(fieldNode),
        schemaPath,
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
