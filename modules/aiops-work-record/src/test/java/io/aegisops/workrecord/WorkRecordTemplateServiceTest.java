package io.aegisops.workrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.audit.AuditService;
import org.junit.jupiter.api.Test;

class WorkRecordTemplateServiceTest {
  private final WorkRecordTemplateRepository templates = mock(WorkRecordTemplateRepository.class);
  private final WorkRecordFieldRepository fields = mock(WorkRecordFieldRepository.class);
  private final AuditService audit = mock(AuditService.class);
  private final WorkRecordTemplateService service =
      new WorkRecordTemplateService(templates, fields, audit);

  @Test
  void createTemplate_shouldValidateName() {
    assertThatThrownBy(
            () ->
                service.createTemplate(
                    "t1", new CreateTemplateRequest("", "daily", null, true, "{}"), "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name");
  }

  @Test
  void createTemplate_shouldRejectNullRequest() {
    assertThatThrownBy(() -> service.createTemplate("t1", null, "u1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createTemplate_shouldRejectMalformedSchemaJson() {
    assertThatThrownBy(
            () ->
                service.createTemplate(
                    "t1", new CreateTemplateRequest("日常记录", "daily", null, true, "not-json"), "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("schemaJson");
  }

  @Test
  void createField_shouldRejectUnsupportedFieldType() {
    CreateFieldRequest request =
        new CreateFieldRequest(
            "字段", "field", "unknown", false, null, "static", null, "[]", false, false, false, 0,
            true);

    assertThatThrownBy(() -> service.createField("t1", "tpl1", request, "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported");
  }

  @Test
  void createField_whenDictOption_shouldRequireDictCode() {
    CreateFieldRequest request =
        new CreateFieldRequest(
            "优先级", "priority", "select", false, null, "dict", "", "[]", true, true, false, 0, true);

    assertThatThrownBy(() -> service.createField("t1", "tpl1", request, "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dictCode");
  }

  @Test
  void createField_shouldRejectMalformedOptionsJson() {
    CreateFieldRequest request =
        new CreateFieldRequest(
            "标签",
            "tags",
            "select",
            false,
            null,
            "static",
            null,
            "{not-json}",
            true,
            true,
            false,
            0,
            true);

    assertThatThrownBy(() -> service.createField("t1", "tpl1", request, "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("optionsJson");
  }

  @Test
  void createField_shouldRejectJsonObjectForOptionsJson() {
    CreateFieldRequest request =
        new CreateFieldRequest(
            "标签", "tags", "select", false, null, "static", null, "{}", true, true, false, 0, true);

    assertThatThrownBy(() -> service.createField("t1", "tpl1", request, "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be a JSON array");
  }

  @Test
  void createTemplate_shouldDelegateToRepositoryAndEmitAudit() {
    WorkRecordTemplate stored =
        new WorkRecordTemplate(
            "tpl1",
            "t1",
            "日常记录",
            "daily",
            null,
            true,
            "{}",
            "u1",
            java.time.OffsetDateTime.now(),
            java.time.OffsetDateTime.now());
    when(templates.create(eq("t1"), any(CreateTemplateRequest.class), eq("u1"))).thenReturn(stored);
    CreateTemplateRequest request = new CreateTemplateRequest("日常记录", "daily", null, true, "{}");

    service.createTemplate("t1", request, "u1");

    verify(templates).create(eq("t1"), any(CreateTemplateRequest.class), eq("u1"));
    verify(audit).record(any());
  }

  @Test
  void updateField_onlyName_keepsOptionsUntouched() {
    WorkRecordField existing =
        new WorkRecordField(
            "f1",
            "t1",
            "tpl1",
            "原名称",
            "tag",
            "select",
            false,
            null,
            "static",
            null,
            "[{\"label\":\"a\",\"value\":\"a\"}]",
            true,
            true,
            false,
            0,
            true,
            java.time.OffsetDateTime.now(),
            java.time.OffsetDateTime.now());
    when(fields.update(eq("t1"), eq("tpl1"), eq("f1"), any(UpdateFieldRequest.class)))
        .thenAnswer(
            inv -> {
              UpdateFieldRequest req = inv.getArgument(3);
              assertThat(req.fieldName()).isEqualTo("新名称");
              assertThat(req.optionsJson()).isNull();
              return java.util.Optional.of(existing);
            });

    service.updateField(
        "t1",
        "tpl1",
        "f1",
        new UpdateFieldRequest("新名称", null, null, null, null, null, null, null, null, null, null),
        "u1");

    verify(fields).update(eq("t1"), eq("tpl1"), eq("f1"), any(UpdateFieldRequest.class));
  }
}
