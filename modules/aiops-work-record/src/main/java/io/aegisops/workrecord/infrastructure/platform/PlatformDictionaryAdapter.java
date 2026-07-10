package io.aegisops.workrecord.infrastructure.platform;

import io.aegisops.platform.dictionary.DictionaryService;
import io.aegisops.workrecord.application.port.WorkRecordDictionaryPort;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PlatformDictionaryAdapter implements WorkRecordDictionaryPort {
  private final DictionaryService dictionaryService;

  public PlatformDictionaryAdapter(DictionaryService dictionaryService) {
    this.dictionaryService = dictionaryService;
  }

  @Override
  public void requireEnabledItem(String tenantId, String dictCode, String itemValue) {
    boolean exists =
        dictionaryService.listItems(tenantId, dictCode).stream()
            .anyMatch(item -> item.enabled() && item.itemValue().equals(itemValue));
    if (!exists) {
      throw new IllegalArgumentException(
          "dict item not found or disabled: " + dictCode + "/" + itemValue);
    }
  }

  @Override
  public Map<String, String> itemLabels(String tenantId, String dictCode) {
    Map<String, String> labels = new LinkedHashMap<>();

    dictionaryService
        .listItems(tenantId, dictCode, true)
        .forEach(
            item ->
                labels.putIfAbsent(
                    item.itemValue(),
                    item.enabled() ? item.itemLabel() : item.itemLabel() + "（已禁用）"));

    return Map.copyOf(labels);
  }
}
