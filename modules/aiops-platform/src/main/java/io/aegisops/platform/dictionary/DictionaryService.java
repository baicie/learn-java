package io.aegisops.platform.dictionary;

import io.aegisops.audit.AuditRecordCommand;
import io.aegisops.audit.AuditService;
import io.aegisops.common.json.JsonPayloads;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DictionaryService {
  private final DictionaryRepository repository;
  private final AuditService audit;

  public DictionaryService(DictionaryRepository repository, AuditService audit) {
    this.repository = repository;
    this.audit = audit;
  }

  public List<DictTypeRecord> listTypes(String tenantId) {
    return repository.listTypes(tenantId);
  }

  public DictTypeRecord createType(
      String tenantId, CreateDictTypeRequest request, String createdBy) {
    if (request == null) {
      throw new IllegalArgumentException("dict request is required");
    }
    requireText(request.dictCode(), "dictCode");
    requireText(request.dictName(), "dictName");
    DictTypeRecord record = repository.createType(tenantId, request, defaultActor(createdBy));
    audit.record(
        new AuditRecordCommand(
            tenantId,
            defaultActor(createdBy),
            "platform.dict_type.create",
            "platform_dict_type",
            record.id(),
            auditDetail("dictCode", record.dictCode(), "dictName", record.dictName())));
    return record;
  }

  public DictTypeRecord updateType(
      String tenantId, String dictCode, UpdateDictTypeRequest request, String actor) {
    requireText(dictCode, "dictCode");
    if (request == null) {
      throw new IllegalArgumentException("update request is required");
    }
    DictTypeRecord updated =
        repository
            .updateType(tenantId, dictCode, request, defaultActor(actor))
            .orElseThrow(() -> new IllegalArgumentException("dict type not found"));
    audit.record(
        new AuditRecordCommand(
            tenantId,
            defaultActor(actor),
            "platform.dict_type.update",
            "platform_dict_type",
            updated.id(),
            auditDetail("dictCode", dictCode, "enabled", String.valueOf(updated.enabled()))));
    return updated;
  }

  public List<DictItemRecord> listItems(String tenantId, String dictCode) {
    return listItems(tenantId, dictCode, false);
  }

  public List<DictItemRecord> listItems(String tenantId, String dictCode, boolean includeDisabled) {
    requireText(dictCode, "dictCode");
    return repository.listItems(tenantId, dictCode, includeDisabled);
  }

  public DictItemRecord createItem(
      String tenantId, String dictCode, CreateDictItemRequest request, String createdBy) {
    if (request == null) {
      throw new IllegalArgumentException("dict item request is required");
    }
    requireText(dictCode, "dictCode");
    requireText(request.itemLabel(), "itemLabel");
    requireText(request.itemValue(), "itemValue");
    String safeExtraJson = JsonPayloads.normalizeObject(request.extraJson(), "extraJson");
    DictItemRecord record =
        repository.createItem(
            tenantId,
            dictCode,
            new CreateDictItemRequest(
                request.itemLabel(),
                request.itemValue(),
                request.color(),
                request.icon(),
                request.description(),
                request.sortOrder(),
                request.enabled(),
                safeExtraJson),
            defaultActor(createdBy));
    audit.record(
        new AuditRecordCommand(
            tenantId,
            defaultActor(createdBy),
            "platform.dict_item.create",
            "platform_dict_item",
            record.id(),
            auditDetail(
                "dictCode",
                dictCode,
                "itemLabel",
                record.itemLabel(),
                "itemValue",
                record.itemValue())));
    return record;
  }

  public DictItemRecord updateItem(
      String tenantId,
      String dictCode,
      String itemId,
      UpdateDictItemRequest request,
      String actor) {
    requireText(dictCode, "dictCode");
    requireText(itemId, "itemId");
    if (request == null) {
      throw new IllegalArgumentException("update request is required");
    }
    DictItemRecord updated =
        repository
            .updateItem(tenantId, dictCode, itemId, request)
            .orElseThrow(() -> new IllegalArgumentException("dict item not found"));
    audit.record(
        new AuditRecordCommand(
            tenantId,
            defaultActor(actor),
            "platform.dict_item.update",
            "platform_dict_item",
            updated.id(),
            auditDetail("dictCode", dictCode, "enabled", String.valueOf(updated.enabled()))));
    return updated;
  }

  private void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
  }

  private String defaultActor(String createdBy) {
    return createdBy == null || createdBy.isBlank() ? "system" : createdBy;
  }

  private String auditDetail(String... keyValues) {
    StringBuilder sb = new StringBuilder("{");
    for (int i = 0; i + 1 < keyValues.length; i += 2) {
      if (i > 0) sb.append(',');
      sb.append('"').append(escape(keyValues[i])).append('"');
      sb.append(':');
      sb.append('"').append(escape(keyValues[i + 1] == null ? "" : keyValues[i + 1])).append('"');
    }
    sb.append('}');
    return sb.toString();
  }

  private String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
