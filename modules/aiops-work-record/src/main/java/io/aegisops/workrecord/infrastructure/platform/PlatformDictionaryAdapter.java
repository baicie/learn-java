package io.aegisops.workrecord.infrastructure.platform;

import io.aegisops.platform.dictionary.DictionaryService;
import io.aegisops.workrecord.application.port.WorkRecordDictionaryPort;
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
}
