package io.aegisops.platform.dictionary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.common.tenant.TenantContext;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DictionaryControllerTest {

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void listTypes_shouldReturnTypes() {
    DictionaryService service = Mockito.mock(DictionaryService.class);
    when(service.listTypes("tenant_1", false))
        .thenReturn(
            List.of(
                new DictTypeRecord(
                    "dt1",
                    "tenant_1",
                    "record_status",
                    "工作记录状态",
                    null,
                    false,
                    true,
                    0,
                    "system",
                    OffsetDateTime.now(),
                    OffsetDateTime.now())));

    TenantContext.setTenantId("tenant_1");
    DictionaryController controller = new DictionaryController(service);

    var response = controller.listTypes(false);

    assertThat(response.success()).isTrue();
    assertThat(response.data()).hasSize(1);
    assertThat(response.data().getFirst().dictCode()).isEqualTo("record_status");
    verify(service).listTypes("tenant_1", false);
  }

  @Test
  void createType_shouldCreateType() {
    DictionaryService service = Mockito.mock(DictionaryService.class);
    DictTypeRecord stored =
        new DictTypeRecord(
            "dt1",
            "tenant_1",
            "record_status",
            "工作记录状态",
            null,
            false,
            true,
            0,
            "system",
            OffsetDateTime.now(),
            OffsetDateTime.now());
    when(service.createType(eq("tenant_1"), any(), eq("system"))).thenReturn(stored);

    TenantContext.setTenantId("tenant_1");
    DictionaryController controller = new DictionaryController(service);

    var response =
        controller.createType(
            new CreateDictTypeRequest("record_status", "工作记录状态", null, null, true), null);

    assertThat(response.success()).isTrue();
    assertThat(response.data().dictCode()).isEqualTo("record_status");
    verify(service).createType(eq("tenant_1"), any(), eq("system"));
  }

  @Test
  void listItems_shouldReturnItems() {
    DictionaryService service = Mockito.mock(DictionaryService.class);
    when(service.listItems("tenant_1", "record_status", false))
        .thenReturn(
            List.of(
                new DictItemRecord(
                    "di1",
                    "tenant_1",
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
                    "system",
                    OffsetDateTime.now(),
                    OffsetDateTime.now())));

    TenantContext.setTenantId("tenant_1");
    DictionaryController controller = new DictionaryController(service);

    var response = controller.listItems("record_status", false);

    assertThat(response.success()).isTrue();
    assertThat(response.data()).hasSize(1);
    assertThat(response.data().getFirst().itemValue()).isEqualTo("draft");
    verify(service).listItems("tenant_1", "record_status", false);
  }

  @Test
  void listItems_whenIncludeDisabled_shouldReturnHistoricalItems() {
    DictionaryService service = Mockito.mock(DictionaryService.class);
    DictItemRecord disabled =
        new DictItemRecord(
            "di1",
            "tenant_1",
            "dt1",
            "已废弃",
            "deprecated",
            null,
            null,
            null,
            false,
            false,
            20,
            "{}",
            "system",
            OffsetDateTime.now(),
            OffsetDateTime.now());
    when(service.listItems("tenant_1", "record_status", true)).thenReturn(List.of(disabled));

    TenantContext.setTenantId("tenant_1");
    DictionaryController controller = new DictionaryController(service);

    var response = controller.listItems("record_status", true);

    assertThat(response.success()).isTrue();
    assertThat(response.data()).hasSize(1);
    assertThat(response.data().getFirst().itemValue()).isEqualTo("deprecated");
    verify(service).listItems("tenant_1", "record_status", true);
  }

  @Test
  void createItem_shouldCreateItem() {
    DictionaryService service = Mockito.mock(DictionaryService.class);
    DictItemRecord stored =
        new DictItemRecord(
            "di1",
            "tenant_1",
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
            "system",
            OffsetDateTime.now(),
            OffsetDateTime.now());
    when(service.createItem(eq("tenant_1"), eq("record_status"), any(), eq("system")))
        .thenReturn(stored);

    TenantContext.setTenantId("tenant_1");
    DictionaryController controller = new DictionaryController(service);

    var response =
        controller.createItem(
            "record_status",
            new CreateDictItemRequest("草稿", "draft", null, null, null, null, true, null),
            null);

    assertThat(response.success()).isTrue();
    assertThat(response.data().itemValue()).isEqualTo("draft");
    verify(service).createItem(eq("tenant_1"), eq("record_status"), any(), eq("system"));
  }

  @Test
  void updateItem_shouldUpdateItem() {
    DictionaryService service = Mockito.mock(DictionaryService.class);
    DictItemRecord stored =
        new DictItemRecord(
            "di1",
            "tenant_1",
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
            "system",
            OffsetDateTime.now(),
            OffsetDateTime.now());
    when(service.updateItem(
            eq("tenant_1"), eq("record_status"), eq("di1"), any(), any(String.class)))
        .thenReturn(stored);

    TenantContext.setTenantId("tenant_1");
    DictionaryController controller = new DictionaryController(service);

    var response =
        controller.updateItem(
            "record_status",
            "di1",
            new UpdateDictItemRequest("草稿", null, null, null, true, null),
            null);

    assertThat(response.success()).isTrue();
    assertThat(response.data().itemValue()).isEqualTo("draft");
    verify(service)
        .updateItem(eq("tenant_1"), eq("record_status"), eq("di1"), any(), any(String.class));
  }
}
