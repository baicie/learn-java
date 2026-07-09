package io.aegisops.workrecord.application.schema;

import static io.aegisops.workrecord.application.schema.WorkRecordSchemaContract.FIELD_EXTENSION_KEY;
import static io.aegisops.workrecord.application.schema.WorkRecordSchemaContract.ROOT_SCHEMA_VERSION_KEY;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aegisops.workrecord.domain.model.FormFieldDescriptor;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WorkRecordSchemaNormalizer {
  private final ObjectMapper objectMapper;

  public WorkRecordSchemaNormalizer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String normalizeSchema(String schemaJson) {
    try {
      JsonNode parsed =
          objectMapper.readTree(schemaJson == null || schemaJson.isBlank() ? "{}" : schemaJson);
      if (!parsed.isObject()) {
        throw new IllegalArgumentException("schemaJson must be object");
      }

      ObjectNode root = (ObjectNode) parsed.deepCopy();
      if (!root.has("type")) {
        root.put("type", "object");
      }
      root.put(ROOT_SCHEMA_VERSION_KEY, WorkRecordSchemaContract.CURRENT_SCHEMA_VERSION);

      normalizeFields(root);

      return objectMapper.writeValueAsString(root);
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("failed to normalize schema", ex);
    }
  }

  public String normalizeDesignerJson(String designerJson) {
    try {
      JsonNode parsed =
          objectMapper.readTree(
              designerJson == null || designerJson.isBlank() ? "{}" : designerJson);
      if (!parsed.isObject()) {
        throw new IllegalArgumentException("designerJson must be object");
      }
      return objectMapper.writeValueAsString(parsed);
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("failed to normalize designerJson", ex);
    }
  }

  public String fieldIndexJson(List<FormFieldDescriptor> fields) {
    try {
      List<FormFieldDescriptor> sorted =
          fields.stream()
              .sorted(
                  Comparator.comparingInt(FormFieldDescriptor::sortOrder)
                      .thenComparing(FormFieldDescriptor::fieldCode))
              .toList();
      return objectMapper.writeValueAsString(sorted);
    } catch (Exception ex) {
      throw new IllegalArgumentException("failed to serialize field index", ex);
    }
  }

  private void normalizeFields(ObjectNode node) {
    JsonNode properties = node.path("properties");
    if (!properties.isObject()) {
      return;
    }

    var names = properties.fieldNames();
    while (names.hasNext()) {
      String name = names.next();
      JsonNode field = properties.get(name);
      if (field.isObject()) {
        ObjectNode fieldObject = (ObjectNode) field;
        normalizeFieldExtension(fieldObject);
        normalizeFields(fieldObject);
      }
    }
  }

  private void normalizeFieldExtension(ObjectNode fieldObject) {
    JsonNode ext = fieldObject.path(FIELD_EXTENSION_KEY);
    if (!ext.isObject()) {
      return;
    }

    ObjectNode extObject = (ObjectNode) ext;

    if (!extObject.has("optionSource") || extObject.path("optionSource").asText().isBlank()) {
      extObject.put("optionSource", "static");
    }
    if (!extObject.has("listVisible")) {
      extObject.put("listVisible", false);
    }
    if (!extObject.has("filterable")) {
      extObject.put("filterable", false);
    }
    if (!extObject.has("exportable")) {
      extObject.put("exportable", true);
    }
    if (!extObject.has("statistical")) {
      extObject.put("statistical", false);
    }

    if (!"dict".equals(extObject.path("optionSource").asText()) && extObject.has("dictCode")) {
      extObject.remove("dictCode");
    }
  }
}