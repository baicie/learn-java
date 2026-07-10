package io.aegisops.platform.dictionary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.platform.audit.PlatformAuditService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DictionaryServiceTest {
  private final DictionaryRepository repository = mock(DictionaryRepository.class);
  private final PlatformAuditService audit = mock(PlatformAuditService.class);
  private final DictionaryService service = new DictionaryService(repository, audit);

  @Test
  void createType_shouldValidateDictCode() {
    assertThatThrownBy(
            () ->
                service.createType("t1", new CreateDictTypeRequest("", "状态", null, 0, true), "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dictCode");
  }

  @Test
  void createType_shouldValidateDictName() {
    assertThatThrownBy(
            () ->
                service.createType(
                    "t1", new CreateDictTypeRequest("record_status", "", null, 0, true), "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dictName");
  }

  @Test
  void createType_shouldRejectNullRequest() {
    assertThatThrownBy(() -> service.createType("t1", null, "u1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createType_shouldDelegateToRepositoryAndEmitAudit() {
    CreateDictTypeRequest request =
        new CreateDictTypeRequest("record_status", "工作记录状态", null, 10, true);
    DictTypeRecord stored =
        new DictTypeRecord(
            "dt1",
            "t1",
            "record_status",
            "工作记录状态",
            null,
            false,
            true,
            10,
            "system",
            OffsetDateTime.now(),
            OffsetDateTime.now());
    when(repository.createType("t1", request, "u1")).thenReturn(stored);

    service.createType("t1", request, "u1");

    verify(repository).createType("t1", request, "u1");
    verify(audit)
        .recordChange(
            eq("t1"),
            eq("u1"),
            eq("platform.dict_type.create"),
            eq("platform_dict_type"),
            eq("dt1"),
            eq(Map.of()),
            eq(stored),
            anyMap());
  }

  @Test
  void createItem_shouldValidateItemValue() {
    assertThatThrownBy(
            () ->
                service.createItem(
                    "t1",
                    "record_status",
                    new CreateDictItemRequest("草稿", "", null, null, null, 0, true, "{}"),
                    "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("itemValue");
  }

  @Test
  void createItem_shouldRejectMalformedJson() {
    assertThatThrownBy(
            () ->
                service.createItem(
                    "t1",
                    "record_status",
                    new CreateDictItemRequest("草稿", "draft", null, null, null, 0, true, "not-json"),
                    "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("extraJson");
  }

  @Test
  void createItem_shouldRejectJsonArray() {
    assertThatThrownBy(
            () ->
                service.createItem(
                    "t1",
                    "record_status",
                    new CreateDictItemRequest("草稿", "draft", null, null, null, 0, true, "[]"),
                    "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be a JSON object");
  }

  @Test
  void createItem_shouldDelegateToRepository() {
    Mockito.reset(repository);
    DictTypeRecord type =
        new DictTypeRecord(
            "dt1",
            "t1",
            "record_status",
            "工作记录状态",
            null,
            false,
            true,
            0,
            "system",
            OffsetDateTime.now(),
            OffsetDateTime.now());
    when(repository.findType("t1", "record_status")).thenReturn(java.util.Optional.of(type));
    DictItemRecord item =
        new DictItemRecord(
            "di1",
            "t1",
            "dt1",
            "草稿",
            "draft",
            null,
            null,
            null,
            false,
            true,
            0,
            "{}",
            "u1",
            OffsetDateTime.now(),
            OffsetDateTime.now());
    when(repository.createItem(any(), Mockito.eq("record_status"), any(), Mockito.eq("u1")))
        .thenReturn(item);

    service.createItem(
        "t1",
        "record_status",
        new CreateDictItemRequest("草稿", "draft", null, null, null, 0, true, null),
        "u1");

    verify(repository)
        .createItem(
            Mockito.eq("t1"),
            Mockito.eq("record_status"),
            Mockito.any(CreateDictItemRequest.class),
            Mockito.eq("u1"));
    verify(audit)
        .recordChange(
            eq("t1"),
            eq("u1"),
            eq("platform.dict_item.create"),
            eq("platform_dict_item"),
            eq("di1"),
            eq(Map.of()),
            eq(item),
            anyMap());
  }

  @Test
  void listItems_byDefault_shouldHideDisabledItems() {
    service.listItems("t1", "record_priority");

    verify(repository).listItems("t1", "record_priority", false);
  }

  @Test
  void listItems_whenIncludeDisabled_shouldReturnHistoricalItems() {
    DictItemRecord disabled =
        new DictItemRecord(
            "di1",
            "t1",
            "dt1",
            "P2",
            "P2",
            null,
            null,
            null,
            false,
            false,
            20,
            "{}",
            "u1",
            OffsetDateTime.now(),
            OffsetDateTime.now());
    when(repository.listItems("t1", "record_priority", true)).thenReturn(List.of(disabled));

    var items = service.listItems("t1", "record_priority", true);

    assertThat(items).extracting(DictItemRecord::itemValue).containsExactly("P2");
    verify(repository).listItems("t1", "record_priority", true);
  }

  @Test
  void updateItemShouldAuditBeforeAndAfter() {
    DictItemRecord before =
        new DictItemRecord(
            "i1",
            "t1",
            "dt1",
            "旧标签",
            "P2",
            null,
            null,
            null,
            false,
            true,
            10,
            "{}",
            "u1",
            OffsetDateTime.now(),
            OffsetDateTime.now());
    DictItemRecord after =
        new DictItemRecord(
            "i1",
            "t1",
            "dt1",
            "新标签",
            "P2",
            null,
            null,
            null,
            false,
            true,
            10,
            "{}",
            "u1",
            OffsetDateTime.now(),
            OffsetDateTime.now());

    when(repository.findItem("t1", "priority", "i1")).thenReturn(java.util.Optional.of(before));
    when(repository.updateItem(eq("t1"), eq("priority"), eq("i1"), any()))
        .thenReturn(java.util.Optional.of(after));

    service.updateItem(
        "t1",
        "priority",
        "i1",
        new UpdateDictItemRequest("新标签", null, null, null, null, null),
        "u1");

    verify(audit)
        .recordChange(
            eq("t1"),
            eq("u1"),
            eq("platform.dict_item.update"),
            eq("platform_dict_item"),
            eq("i1"),
            eq(before),
            eq(after),
            eq(Map.of("dictCode", "priority", "itemValue", after.itemValue())));
  }

  @Test
  void disableItem_shouldReturnDisabledItemAndAudit() {
    DictItemRecord before =
        new DictItemRecord(
            "item-1",
            "tenant-1",
            "dict-1",
            "P2",
            "P2",
            null,
            null,
            null,
            false,
            true,
            20,
            "{}",
            "u1",
            OffsetDateTime.now(),
            OffsetDateTime.now());
    DictItemRecord disabled =
        new DictItemRecord(
            "item-1",
            "tenant-1",
            "dict-1",
            "P2",
            "P2",
            null,
            null,
            null,
            false,
            false,
            20,
            "{}",
            "u1",
            OffsetDateTime.now(),
            OffsetDateTime.now());

    when(repository.findItem("tenant-1", "record_priority", "item-1"))
        .thenReturn(java.util.Optional.of(before));
    when(repository.disableItem("tenant-1", "record_priority", "item-1"))
        .thenReturn(java.util.Optional.of(disabled));

    DictItemRecord result = service.disableItem("tenant-1", "record_priority", "item-1", "u1");

    assertThat(result.enabled()).isFalse();
    verify(audit)
        .recordChange(
            eq("tenant-1"),
            eq("u1"),
            eq("platform.dict_item.disable"),
            eq("platform_dict_item"),
            eq("item-1"),
            eq(before),
            eq(disabled),
            anyMap());
  }
}
