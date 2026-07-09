package io.aegisops.workrecord.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.FormFieldDescriptor;
import io.aegisops.workrecord.domain.model.OptionSource;
import io.aegisops.workrecord.domain.rule.FieldCodeRules;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordSchemaService {
  private final ObjectMapper objectMapper;

  public WorkRecordSchemaService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String normalizeObject(String json, String fieldName) {
    try {
      JsonNode node = objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
      if (!node.isObject()) {
        throw new IllegalArgumentException(fieldName + " must be JSON object");
      }
      return objectMapper.writeValueAsString(node);
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid " + fieldName, ex);
    }
  }

  public List<FormFieldDescriptor> extractFields(String schemaJson) {
    try {
      JsonNode root = objectMapper.readTree(normalizeObject(schemaJson, "schemaJson"));
      JsonNode properties = root.path("properties");
      List<FormFieldDescriptor> result = new ArrayList<>();
      if (!properties.isObject()) {
        return result;
      }

      int index = 0;
      var names = properties.fieldNames();
      while (names.hasNext()) {
        String propertyName = names.next();
        JsonNode field = properties.get(propertyName);
        JsonNode ext = field.path("x-work-record");

        String fieldCode = text(ext, "fieldCode", propertyName);
        FieldCodeRules.validate(fieldCode);

        String title = text(field, "title", fieldCode);
        FieldType fieldType = FieldType.from(text(ext, "fieldType", inferType(field)));
        OptionSource optionSource = OptionSource.from(text(ext, "optionSource", "static"));
        String dictCode = nullableText(ext, "dictCode");

        if (optionSource == OptionSource.DICT && (dictCode == null || dictCode.isBlank())) {
          throw new IllegalArgumentException("dictCode is required for field: " + fieldCode);
        }

        result.add(
            new FormFieldDescriptor(
                title,
                fieldCode,
                fieldType,
                field.path("required").asBoolean(false) || ext.path("required").asBoolean(false),
                optionSource,
                dictCode,
                normalizeOptions(field.path("enum")),
                ".properties." + propertyName,
                ext.path("listVisible").asBoolean(false),
                ext.path("filterable").asBoolean(false),
                ext.path("exportable").asBoolean(true),
                ext.path("statistical").asBoolean(false),
                ext.path("sortOrder").asInt(index++)));
      }
      return result;
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("failed to extract form fields", ex);
    }
  }

  public String toFieldIndexJson(List<FormFieldDescriptor> descriptors) {
    try {
      return objectMapper.writeValueAsString(descriptors);
    } catch (Exception ex) {
      throw new IllegalArgumentException("failed to serialize field index", ex);
    }
  }

  private String inferType(JsonNode field) {
    String component = field.path("x-component").asText("");
    return switch (component) {
      case "Textarea" -> "textarea";
      case "Number", "NumberPicker" -> "number";
      case "DatePicker", "Date" -> "date";
      case "DateTimePicker", "DateTime" -> "datetime";
      case "Select" -> "select";
      case "MultiSelect" -> "multi_select";
      case "Switch" -> "boolean";
      default -> "text";
    };
  }

  private String normalizeOptions(JsonNode node) {
    try {
      if (node == null || node.isMissingNode() || node.isNull()) {
        return "[]";
      }
      if (!node.isArray()) {
        return "[]";
      }
      return objectMapper.writeValueAsString(node);
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid options", ex);
    }
  }

  private String text(JsonNode node, String field, String fallback) {
    String value = nullableText(node, field);
    return value == null || value.isBlank() ? fallback : value;
  }

  private String nullableText(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() ? null : value.asText();
  }
}
