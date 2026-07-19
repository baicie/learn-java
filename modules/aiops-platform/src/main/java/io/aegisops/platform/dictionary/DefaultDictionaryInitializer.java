package io.aegisops.platform.dictionary;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 首启动幂等 seed：把 §6.15.4 列出的默认平台字典写入当前已存在的每个租户。
 *
 * <p>写入策略：每个租户独立复制，避免跨租户 FK 冲突；只补缺失项，不覆盖用户已有数据。 这样即使重启或重复执行也安全。
 */
@Component
public class DefaultDictionaryInitializer {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultDictionaryInitializer.class);

  private final DefaultDictionarySupport support;
  private final DictionaryService service;

  public DefaultDictionaryInitializer(DefaultDictionarySupport support, DictionaryService service) {
    this.support = support;
    this.service = service;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void seedDefaults() {
    List<String> tenants = support.listActiveTenants();
    if (tenants.isEmpty()) {
      LOG.info("default dictionary seed skipped: no active tenants");
      return;
    }
    int tenantCount = 0;
    int itemCount = 0;
    for (String tenantId : tenants) {
      tenantCount += seedForTenant(tenantId);
      itemCount += seedItemsForTenant(tenantId);
    }
    LOG.info("default dictionary seed done: tenants={} items={}", tenantCount, itemCount);
  }

  private int seedForTenant(String tenantId) {
    int created = 0;
    Set<String> existingCodes =
        service.listTypes(tenantId, true).stream()
            .map(DictTypeRecord::dictCode)
            .collect(Collectors.toSet());
    for (DefaultDictType type : DefaultDictType.values()) {
      if (existingCodes.contains(type.code())) {
        continue;
      }
      service.createType(
          tenantId,
          new CreateDictTypeRequest(type.code(), type.displayName(), type.description(), 0, true),
          "system-bootstrap");
      created++;
    }
    return created;
  }

  private int seedItemsForTenant(String tenantId) {
    int created = 0;
    Map<String, List<DefaultDictItem>> itemsByType = DefaultDictItems.itemsByType();
    for (Map.Entry<String, List<DefaultDictItem>> entry : itemsByType.entrySet()) {
      String dictCode = entry.getKey();
      List<DictItemRecord> existing = service.listItems(tenantId, dictCode, true);
      for (DefaultDictItem item : entry.getValue()) {
        if (existing.stream().anyMatch(i -> i.itemValue().equals(item.value()))) {
          continue;
        }
        service.createItem(
            tenantId,
            dictCode,
            new CreateDictItemRequest(
                item.label(),
                item.value(),
                item.color(),
                null,
                item.description(),
                item.sortOrder(),
                true,
                "{}"),
            "system-bootstrap");
        created++;
      }
    }
    return created;
  }
}
