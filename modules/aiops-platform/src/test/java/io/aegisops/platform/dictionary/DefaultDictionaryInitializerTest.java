package io.aegisops.platform.dictionary;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultDictionaryInitializerTest {
  private final DefaultDictionarySupport support = mock(DefaultDictionarySupport.class);
  private final DictionaryService service = mock(DictionaryService.class);
  private final DefaultDictionaryInitializer initializer =
      new DefaultDictionaryInitializer(support, service);

  @Test
  void seedDefaults_shouldNotRecreateDisabledTypesOrItems() {
    DefaultDictType type = DefaultDictType.values()[0];
    DefaultDictItem item = DefaultDictItems.itemsByType().get(type.code()).get(0);
    OffsetDateTime now = OffsetDateTime.now();
    DictTypeRecord disabledType =
        new DictTypeRecord(
            "dt1",
            "t1",
            type.code(),
            type.displayName(),
            type.description(),
            true,
            false,
            0,
            "system-bootstrap",
            now,
            now);
    DictItemRecord disabledItem =
        new DictItemRecord(
            "di1",
            "t1",
            "dt1",
            item.label(),
            item.value(),
            item.color(),
            null,
            item.description(),
            true,
            false,
            item.sortOrder(),
            "{}",
            "system-bootstrap",
            now,
            now);

    when(support.listActiveTenants()).thenReturn(List.of("t1"));
    when(service.listTypes("t1", true)).thenReturn(List.of(disabledType));
    when(service.listItems("t1", type.code(), true)).thenReturn(List.of(disabledItem));

    initializer.seedDefaults();

    verify(service).listTypes("t1", true);
    verify(service).listItems("t1", type.code(), true);
    verify(service, never())
        .createType(eq("t1"), argThat(request -> request.dictCode().equals(type.code())), any());
    verify(service, never())
        .createItem(
            eq("t1"),
            eq(type.code()),
            argThat(request -> request.itemValue().equals(item.value())),
            any());
  }
}
