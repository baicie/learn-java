package io.aegisops.workrecord.infrastructure.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.platform.dictionary.DictItemRecord;
import io.aegisops.platform.dictionary.DictionaryService;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlatformDictionaryAdapterTest {
  private final DictionaryService dictionaryService = mock(DictionaryService.class);
  private final PlatformDictionaryAdapter adapter =
      new PlatformDictionaryAdapter(dictionaryService);

  @Test
  void returnsEnabledItemValuesInDictionaryOrder() {
    when(dictionaryService.listItems("tenant-1", "record_priority"))
        .thenReturn(List.of(item("P1", 10), item("P2", 20)));

    assertThat(adapter.enabledItemValues("tenant-1", "record_priority"))
        .containsExactly("P1", "P2");
    verify(dictionaryService).listItems("tenant-1", "record_priority");
  }

  private static DictItemRecord item(String value, int sortOrder) {
    return new DictItemRecord(
        "item-" + value,
        "tenant-1",
        "dict-type-1",
        value,
        value,
        null,
        null,
        null,
        false,
        true,
        sortOrder,
        "{}",
        "system",
        null,
        null);
  }
}
