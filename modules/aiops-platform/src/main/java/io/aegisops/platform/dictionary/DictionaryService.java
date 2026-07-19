package io.aegisops.platform.dictionary;

import io.aegisops.common.json.JsonPayloads;
import io.aegisops.platform.audit.PlatformAuditService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DictionaryService {
  private final DictionaryRepository repository;
  private final PlatformAuditService audit;

  public DictionaryService(DictionaryRepository repository, PlatformAuditService audit) {
    this.repository = repository;
    this.audit = audit;
  }

  public List<DictTypeRecord> listTypes(String tenantId) {
    return repository.listTypes(tenantId, false);
  }

  public List<DictTypeRecord> listTypes(String tenantId, boolean includeDisabled) {
    return repository.listTypes(tenantId, includeDisabled);
  }

  @Transactional
  public DictTypeRecord createType(String tenantId, CreateDictTypeRequest request, String actor) {
    if (request == null) {
      throw new IllegalArgumentException("dict request is required");
    }

    requireText(request.dictCode(), "dictCode");
    requireText(request.dictName(), "dictName");

    String effectiveActor = defaultActor(actor);

    DictTypeRecord created = repository.createType(tenantId, request, effectiveActor);

    audit.recordChange(
        tenantId,
        effectiveActor,
        "platform.dict_type.create",
        "platform_dict_type",
        created.id(),
        Map.of(),
        created,
        Map.of("dictCode", created.dictCode()));

    return created;
  }

  @Transactional
  public DictTypeRecord updateType(
      String tenantId, String dictCode, UpdateDictTypeRequest request, String actor) {
    requireText(dictCode, "dictCode");

    if (request == null) {
      throw new IllegalArgumentException("update request is required");
    }

    DictTypeRecord before =
        repository
            .findType(tenantId, dictCode)
            .orElseThrow(() -> new IllegalArgumentException("dict type not found"));

    DictTypeRecord after =
        repository
            .updateType(tenantId, dictCode, request, defaultActor(actor))
            .orElseThrow(() -> new IllegalArgumentException("dict type not found"));

    audit.recordChange(
        tenantId,
        defaultActor(actor),
        "platform.dict_type.update",
        "platform_dict_type",
        after.id(),
        before,
        after,
        Map.of("dictCode", dictCode));

    return after;
  }

  @Transactional
  public DictTypeRecord disableType(String tenantId, String dictCode, String actor) {
    requireText(dictCode, "dictCode");

    DictTypeRecord before =
        repository
            .findType(tenantId, dictCode)
            .orElseThrow(() -> new IllegalArgumentException("dict type not found"));

    DictTypeRecord after =
        repository
            .disableType(tenantId, dictCode)
            .orElseThrow(() -> new IllegalArgumentException("dict type not found"));

    audit.recordChange(
        tenantId,
        defaultActor(actor),
        "platform.dict_type.disable",
        "platform_dict_type",
        after.id(),
        before,
        after,
        Map.of("dictCode", dictCode));

    return after;
  }

  public List<DictItemRecord> listItems(String tenantId, String dictCode) {
    return listItems(tenantId, dictCode, false);
  }

  public List<DictItemRecord> listItems(String tenantId, String dictCode, boolean includeDisabled) {
    requireText(dictCode, "dictCode");

    if (!includeDisabled
        && repository.findType(tenantId, dictCode).filter(type -> !type.enabled()).isPresent()) {
      return List.of();
    }

    return repository.listItems(tenantId, dictCode, includeDisabled);
  }

  @Transactional
  public DictItemRecord createItem(
      String tenantId, String dictCode, CreateDictItemRequest request, String actor) {
    if (request == null) {
      throw new IllegalArgumentException("dict item request is required");
    }

    requireText(dictCode, "dictCode");
    requireText(request.itemLabel(), "itemLabel");
    requireText(request.itemValue(), "itemValue");

    String safeExtraJson = JsonPayloads.normalizeObject(request.extraJson(), "extraJson");

    CreateDictItemRequest normalized =
        new CreateDictItemRequest(
            request.itemLabel(),
            request.itemValue(),
            request.color(),
            request.icon(),
            request.description(),
            request.sortOrder(),
            request.enabled(),
            safeExtraJson);

    String effectiveActor = defaultActor(actor);

    DictItemRecord created = repository.createItem(tenantId, dictCode, normalized, effectiveActor);

    audit.recordChange(
        tenantId,
        effectiveActor,
        "platform.dict_item.create",
        "platform_dict_item",
        created.id(),
        Map.of(),
        created,
        Map.of("dictCode", dictCode, "itemValue", created.itemValue()));

    return created;
  }

  @Transactional
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

    DictItemRecord before =
        repository
            .findItem(tenantId, dictCode, itemId)
            .orElseThrow(() -> new IllegalArgumentException("dict item not found"));

    DictItemRecord after =
        repository
            .updateItem(tenantId, dictCode, itemId, request)
            .orElseThrow(() -> new IllegalArgumentException("dict item not found"));

    audit.recordChange(
        tenantId,
        defaultActor(actor),
        "platform.dict_item.update",
        "platform_dict_item",
        after.id(),
        before,
        after,
        Map.of("dictCode", dictCode, "itemValue", after.itemValue()));

    return after;
  }

  @Transactional
  public DictItemRecord disableItem(String tenantId, String dictCode, String itemId, String actor) {
    requireText(dictCode, "dictCode");
    requireText(itemId, "itemId");

    DictItemRecord before =
        repository
            .findItem(tenantId, dictCode, itemId)
            .orElseThrow(() -> new IllegalArgumentException("dict item not found"));

    DictItemRecord after =
        repository
            .disableItem(tenantId, dictCode, itemId)
            .orElseThrow(() -> new IllegalArgumentException("dict item not found"));

    audit.recordChange(
        tenantId,
        defaultActor(actor),
        "platform.dict_item.disable",
        "platform_dict_item",
        after.id(),
        before,
        after,
        Map.of("dictCode", dictCode, "itemValue", after.itemValue()));

    return after;
  }

  private void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
  }

  private String defaultActor(String actor) {
    return actor == null || actor.isBlank() ? "system" : actor;
  }
}
