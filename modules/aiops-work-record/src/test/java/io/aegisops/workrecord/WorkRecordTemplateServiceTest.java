package io.aegisops.workrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.audit.AuditService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkRecordTemplateServiceTest {
  private final WorkRecordTemplateRepository templates = mock(WorkRecordTemplateRepository.class);
  private final WorkRecordFieldRepository fields = mock(WorkRecordFieldRepository.class);
  private final WorkRecordSchemaService schemaService = new WorkRecordSchemaService();
  private final WorkRecordFieldIndexService fieldIndexService =
      new WorkRecordFieldIndexService(fields);
  private final AuditService audit = mock(AuditService.class);
  private WorkRecordTemplateService service;

  @BeforeEach
  void setup() {
    service =
        new WorkRecordTemplateService(
            templates, fields, schemaService, fieldIndexService, audit);
  }

  @Test
  void createTemplate_shouldValidateName() {
    assertThatThrownBy(
            () ->
                service.createTemplate(
                    "t1",
                    new CreateTemplateRequest("", "daily", null, true, "{}", null),
                    "u1"))
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
                    "t1",
                    new CreateTemplateRequest("日常记录", "daily", null, true, "not-json", null),
                    "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("schemaJson");
  }

  @Test
  void createField_shouldRejectUnsupportedFieldType() {
    CreateFieldRequest request =
        new CreateFieldRequest(
            "字段", "field", "unknown", false, null, "static", null, "[]", false, false,
            false, 0, true, null);

    assertThatThrownBy(() -> service.createField("t1", "tpl1", request, "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported");
  }

  @Test
  void createField_whenDictOption_shouldRequireDictCode() {
    CreateFieldRequest request =
        new CreateFieldRequest(
            "优先级", "priority", "select", false, null, "dict", "", "[]", true, true, false, 0,
            true, null);

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
            true,
            null);

    assertThatThrownBy(() -> service.createField("t1", "tpl1", request, "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("optionsJson");
  }

  @Test
  void createField_shouldRejectJsonObjectForOptionsJson() {
    CreateFieldRequest request =
        new CreateFieldRequest(
            "标签",
            "tags",
            "select",
            false,
            null,
            "static",
            null,
            "{}",
            true,
            true,
            false,
            0,
            true,
            null);

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
            "{}",
            "u1",
            OffsetDateTime.now(),
            OffsetDateTime.now());
    when(templates.create(eq("t1"), any(CreateTemplateRequest.class), eq("u1")))
        .thenReturn(stored);
    CreateTemplateRequest request =
        new CreateTemplateRequest("日常记录", "daily", null, true, "{}", "{}");

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
            ".properties.tag",
            OffsetDateTime.now(),
            OffsetDateTime.now());
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

  @Test
  void saveSchema_shouldRejectReservedFieldCode() {
    // mock fields.list 任意参数都返回空列表，确保 syncFields 空路径走通，
    // 校验在 syncFields 内部对 descriptors 遍历时触发保留字段冲突。
    when(fields.list(any(), any())).thenReturn(List.of());
    TemplateSchemaRequest request =
        new TemplateSchemaRequest(
            "{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\",\"title\":\"标题\"}}}",
            "{}",
            List.of(
                new CreateFieldRequest(
                    "标题",
                    "title",
                    "text",
                    true,
                    null,
                    "static",
                    null,
                    "[]",
                    true,
                    true,
                    false,
                    10,
                    true,
                    ".properties.title")));

    assertThatThrownBy(() -> service.saveSchema("t1", "tpl1", request, "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reserved");
  }

  @Test
  void saveSchema_shouldNormalizeSchemaAndSyncFields() {
    WorkRecordTemplate stored =
        new WorkRecordTemplate(
            "tpl1",
            "t1",
            "日常记录",
            "daily",
            null,
            true,
            "{\"type\":\"object\"}",
            "{}",
            "u1",
            OffsetDateTime.now(),
            OffsetDateTime.now());
    when(templates.updateSchema(eq("t1"), eq("tpl1"), any(String.class), any(String.class)))
        .thenReturn(java.util.Optional.of(stored));
    when(fields.list(eq("t1"), eq("tpl1"))).thenReturn(List.of());
    TemplateSchemaRequest request =
        new TemplateSchemaRequest(
            "{\"type\":\"object\"}",
            "{}",
            List.of(
                new CreateFieldRequest(
                    "优先级",
                    "priority",
                    "boolean",
                    false,
                    null,
                    "static",
                    null,
                    "[]",
                    true,
                    true,
                    false,
                    10,
                    true,
                    ".properties.priority")));

    WorkRecordTemplate result = service.saveSchema("t1", "tpl1", request, "u1");

    assertThat(result.id()).isEqualTo("tpl1");
    // updateSchema 接受 4 参数：tenantId, templateId, schemaJson, designerJson
    verify(templates)
        .updateSchema(eq("t1"), eq("tpl1"), any(String.class), any(String.class));
    // 字段替换使用 syncFields 路径（通过 saveSchema 间接调用）
    verify(audit).record(any());
  }

  @Test
  void saveSchema_normalizeEmptySchema() {
    WorkRecordTemplate stored =
        new WorkRecordTemplate(
            "tpl1",
            "t1",
            "日常记录",
            "daily",
            null,
            true,
            "{}",
            "{}",
            "u1",
            OffsetDateTime.now(),
            OffsetDateTime.now());
    when(templates.updateSchema(eq("t1"), eq("tpl1"), any(String.class), any(String.class)))
        .thenReturn(java.util.Optional.of(stored));
    when(fields.list(eq("t1"), eq("tpl1"))).thenReturn(List.of());

    // 空 schema 不抛异常
    service.saveSchema("t1", "tpl1", new TemplateSchemaRequest(null, null, List.of()), "u1");

    verify(templates)
        .updateSchema(eq("t1"), eq("tpl1"), eq("{}"), any(String.class));
  }
}
