package io.aegisops.platform.dictionary;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.audit.AuditRecordCommand;
import io.aegisops.audit.AuditService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DictionaryServiceTest {
  private final DictionaryRepository repository = mock(DictionaryRepository.class);
  private final AuditService audit = mock(AuditService.class);
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
    verify(audit).record(any(AuditRecordCommand.class));
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
    verify(audit).record(any(AuditRecordCommand.class));
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

    org.assertj.core.api.Assertions.assertThat(items).extracting(DictItemRecord::itemValue).containsExactly("P2");
    verify(repository).listItems("t1", "record_priority", true);
  }
}
