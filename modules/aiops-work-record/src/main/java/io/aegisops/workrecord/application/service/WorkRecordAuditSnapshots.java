package io.aegisops.workrecord.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aegisops.workrecord.domain.model.WorkRecord;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import io.aegisops.workrecord.domain.model.WorkRecordTemplateVersion;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WorkRecordAuditSnapshots {
  private final ObjectMapper objectMapper;

  public WorkRecordAuditSnapshots(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public Map<String, Object> record(WorkRecord value) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", value.id());
    result.put("templateId", value.templateId());
    result.put("templateVersionId", value.templateVersionId());
    result.put("title", value.title());
    result.put("status", value.status().value());
    result.put("ownerId", value.ownerId());
    result.put("creatorId", value.creatorId());
    result.put("recordTime", value.recordTime());
    result.put("builtinData", parseObject(value.builtinDataJson()));
    result.put("customData", parseObject(value.customDataJson()));
    result.put("rowVersion", value.rowVersion());
    result.put("createdAt", value.createdAt());
    result.put("updatedAt", value.updatedAt());
    result.put("deletedAt", value.deletedAt());
    return result;
  }

  public Map<String, Object> recordTombstone(WorkRecord value, OffsetDateTime deletedAt) {
    Map<String, Object> result = new LinkedHashMap<>(record(value));
    result.put("deleted", true);
    result.put("deletedAt", deletedAt);
    return result;
  }

  public Map<String, Object> template(WorkRecordTemplate value) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", value.id());
    result.put("code", value.code());
    result.put("name", value.name());
    result.put("description", value.description());
    result.put("status", value.status().value());
    result.put("enabled", value.enabled());
    result.put("isDefault", value.isDefault());
    result.put("currentVersionId", value.currentVersionId());
    result.put("draftSchema", parseObject(value.draftSchemaJson()));
    result.put("draftDesigner", parseObject(value.draftDesignerJson()));
    result.put("createdBy", value.createdBy());
    result.put("createdAt", value.createdAt());
    result.put("updatedAt", value.updatedAt());
    result.put("deletedAt", value.deletedAt());
    return result;
  }

  public Map<String, Object> version(WorkRecordTemplateVersion value) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", value.id());
    result.put("templateId", value.templateId());
    result.put("versionNo", value.versionNo());
    result.put("versionName", value.versionName());
    result.put("schema", parseObject(value.schemaJson()));
    result.put("designer", parseObject(value.designerJson()));
    result.put("fieldIndex", parseJson(value.fieldIndexJson()));
    result.put("publishedBy", value.publishedBy());
    result.put("publishedAt", value.publishedAt());
    return result;
  }

  public Map<String, Object> field(WorkRecordField value) {
    Map<String, Object> result = new LinkedHashMap<>(fieldSemantic(value));
    result.put("id", value.id());
    result.put("templateVersionId", value.templateVersionId());
    result.put("createdAt", value.createdAt());
    result.put("updatedAt", value.updatedAt());
    return result;
  }

  public Map<String, Object> fieldSemantic(WorkRecordField value) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("templateId", value.templateId());
    result.put("fieldName", value.fieldName());
    result.put("fieldCode", value.fieldCode());
    result.put("fieldType", value.fieldType().value());
    result.put("required", value.required());
    result.put("defaultValue", value.defaultValue());
    result.put("optionSource", value.optionSource().value());
    result.put("dictCode", value.dictCode());
    result.put("options", parseJson(value.optionsJson()));
    result.put("schemaPath", value.schemaPath());
    result.put("listVisible", value.listVisible());
    result.put("filterable", value.filterable());
    result.put("exportable", value.exportable());
    result.put("statistical", value.statistical());
    result.put("sortOrder", value.sortOrder());
    result.put("enabled", value.enabled());
    return result;
  }

  private JsonNode parseObject(String raw) {
    JsonNode node = parseJson(raw);
    if (node.isObject()) {
      return node;
    }

    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set("_value", node);
    return wrapper;
  }

  private JsonNode parseJson(String raw) {
    if (raw == null || raw.isBlank()) {
      return objectMapper.createObjectNode();
    }

    try {
      JsonNode node = objectMapper.readTree(raw);
      return node == null ? objectMapper.createObjectNode() : node;
    } catch (Exception ex) {
      ObjectNode invalid = objectMapper.createObjectNode();
      invalid.put("_invalidJson", true);
      invalid.put("_raw", raw);
      return invalid;
    }
  }
}
