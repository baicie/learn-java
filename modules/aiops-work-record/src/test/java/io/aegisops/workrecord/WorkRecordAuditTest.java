package io.aegisops.workrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.audit.AuditRecordCommand;
import io.aegisops.audit.AuditService;
import io.aegisops.security.UserPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Phase 07 工作记录审计测试。
 *
 * <p>验证关键操作（创建/更新/删除/导出/schema 更新/字段变更）均触发审计，且审计 detail 不含完整 customDataJson 等敏感负载。
 */
class WorkRecordAuditTest {

  private WorkRecordRepository repository;
  private WorkRecordFieldRepository fieldRepository;
  private WorkRecordTemplateRepository templateRepository;
  private WorkRecordSchemaService schemaService;
  private WorkRecordFieldIndexService fieldIndexService;
  private WorkRecordQueryService queryService;
  private AuditService audit;
  private WorkRecordService recordService;
  private WorkRecordTemplateService templateService;
  private WorkRecordExportService exportService;

  @BeforeEach
  void setUp() {
    repository = mock(WorkRecordRepository.class);
    fieldRepository = mock(WorkRecordFieldRepository.class);
    templateRepository = mock(WorkRecordTemplateRepository.class);
    schemaService = mock(WorkRecordSchemaService.class);
    fieldIndexService = mock(WorkRecordFieldIndexService.class);
    audit = mock(AuditService.class);

    recordService = new WorkRecordService(repository, fieldRepository, audit);
    templateService =
        new WorkRecordTemplateService(
            templateRepository, fieldRepository, schemaService, fieldIndexService, audit);
    exportService =
        new WorkRecordExportService(
            repository, fieldRepository, audit, new WorkRecordProperties());

    // queryService 仅在 metadata 中用，这里无需完整 mock
    queryService = mock(WorkRecordQueryService.class);
  }

  private static UserPrincipal principal(String id, String... authorities) {
    List<GrantedAuthority> auths =
        java.util.Arrays.stream(authorities)
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toUnmodifiableList());
    UserPrincipal p = mock(UserPrincipal.class);
    when(p.id()).thenReturn(id);
    doReturn(auths).when(p).getAuthorities();
    return p;
  }

  private static WorkRecord sampleRecord(String id, String ownerId, String creatorId) {
    OffsetDateTime now = OffsetDateTime.now();
    return new WorkRecord(
        id, "t1", "tpl1", "标题", "draft", ownerId, creatorId, now, "{}", "{}", now, now);
  }

  @Nested
  class RecordAudit {

    @Test
    void recordCreateWritesAudit() {
      WorkRecord stored = sampleRecord("r1", "u1", "u1");
      when(fieldRepository.list(eq("t1"), eq("tpl1")))
          .thenReturn(
              List.of(
                  new WorkRecordField(
                      "f1",
                      "t1",
                      "tpl1",
                      "memo",
                      "memo",
                      "text",
                      false,
                      null,
                      "static",
                      null,
                      "[]",
                      true,
                      false,
                      true,
                      false,
                      0,
                      true,
                      ".properties.memo",
                      OffsetDateTime.now(),
                      OffsetDateTime.now())));
      when(repository.create(any(), any(), eq("u1"))).thenReturn(stored);

      recordService.create(
          "t1",
          new CreateWorkRecordRequest(
              "tpl1", "标题", "draft", "u1", OffsetDateTime.now(), "{}", "{}"),
          "u1");

      ArgumentCaptor<AuditRecordCommand> captor = ArgumentCaptor.forClass(AuditRecordCommand.class);
      verify(audit).record(captor.capture());
      AuditRecordCommand cmd = captor.getValue();
      assertThat(cmd.action()).isEqualTo("work_record.record.create");
      assertThat(cmd.targetId()).isEqualTo("r1");
      assertThat(cmd.actorUserId()).isEqualTo("u1");
      // detail 不应包含完整 customDataJson
      assertThat(cmd.detailJson()).doesNotContain("\"customData\"");
    }

    @Test
    void recordUpdateWritesAudit() {
      WorkRecord existing = sampleRecord("r1", "u1", "u1");
      WorkRecord updated = sampleRecord("r1", "u1", "u1");
      when(repository.find("t1", "r1")).thenReturn(Optional.of(existing));
      when(repository.update(eq("t1"), eq("r1"), any(UpdateWorkRecordRequest.class)))
          .thenReturn(Optional.of(updated));
      UserPrincipal user = principal("u1", "work-record:write", "work-record:read:self");

      recordService.update(
          "t1", "r1", new UpdateWorkRecordRequest("新", null, null, null, null, null), user);

      ArgumentCaptor<AuditRecordCommand> captor = ArgumentCaptor.forClass(AuditRecordCommand.class);
      verify(audit).record(captor.capture());
      AuditRecordCommand cmd = captor.getValue();
      assertThat(cmd.action()).isEqualTo("work_record.record.update");
      assertThat(cmd.targetId()).isEqualTo("r1");
    }

    @Test
    void recordDeleteWritesAudit() {
      WorkRecord existing = sampleRecord("r1", "u1", "u1");
      when(repository.find("t1", "r1")).thenReturn(Optional.of(existing));
      UserPrincipal user = principal("u1", "work-record:delete", "work-record:read:self");

      recordService.delete("t1", "r1", user);

      ArgumentCaptor<AuditRecordCommand> captor = ArgumentCaptor.forClass(AuditRecordCommand.class);
      verify(audit).record(captor.capture());
      AuditRecordCommand cmd = captor.getValue();
      assertThat(cmd.action()).isEqualTo("work_record.record.delete");
      assertThat(cmd.targetId()).isEqualTo("r1");
    }
  }

  @Nested
  class TemplateAudit {

    @Test
    void templateCreateWritesAudit() {
      WorkRecordTemplate stored =
          new WorkRecordTemplate(
              "tpl1",
              "t1",
              "日报模板",
              "daily",
              null,
              true,
              "{}",
              "{}",
              null,
              OffsetDateTime.now(),
              OffsetDateTime.now());
      when(templateRepository.create(eq("t1"), any(), eq("admin"))).thenReturn(stored);

      templateService.createTemplate(
          "t1", new CreateTemplateRequest("日报模板", "daily", "描述", true, "{}", "{}"), "admin");

      ArgumentCaptor<AuditRecordCommand> captor = ArgumentCaptor.forClass(AuditRecordCommand.class);
      verify(audit).record(captor.capture());
      AuditRecordCommand cmd = captor.getValue();
      assertThat(cmd.action()).isEqualTo("work_record.template.create");
      assertThat(cmd.targetType()).isEqualTo("wr_template");
      assertThat(cmd.targetId()).isEqualTo("tpl1");
      // detail 应包含 code 与 name，但不应包含完整 schemaJson
      assertThat(cmd.detailJson()).doesNotContain("properties");
    }

    @Test
    void templateSchemaUpdateWritesFieldCount() {
      WorkRecordTemplate updated =
          new WorkRecordTemplate(
              "tpl1",
              "t1",
              "日报模板",
              "daily",
              null,
              true,
              "{}",
              "{}",
              null,
              OffsetDateTime.now(),
              OffsetDateTime.now());
      when(schemaService.normalize("{}")).thenReturn("{}");
      when(schemaService.normalize(any())).thenReturn("{}");
      // extractFields 返回 1 个描述符
      when(schemaService.extractFields("{}")).thenReturn(List.of());
      when(fieldRepository.list("t1", "tpl1")).thenReturn(List.of());
      // syncFields 不抛（带 actor 参数）
      org.mockito.Mockito.doNothing()
          .when(fieldIndexService)
          .syncFields(eq("t1"), eq("tpl1"), any(), any(), eq("admin"));
      when(templateRepository.updateSchema(eq("t1"), eq("tpl1"), eq("{}"), eq("{}")))
          .thenReturn(Optional.of(updated));

      // 用包含 1 个字段的 schema 走一次
      when(schemaService.extractFields("{}"))
          .thenReturn(
              List.of(
                  new FormilyFieldDescriptor(
                      "memo",
                      "备注",
                      "text",
                      "static",
                      null,
                      true,
                      true,
                      false,
                      ".properties.memo")));

      templateService.saveSchema(
          "t1", "tpl1", new TemplateSchemaRequest("{}", "{}", List.of()), "admin");

      ArgumentCaptor<AuditRecordCommand> captor = ArgumentCaptor.forClass(AuditRecordCommand.class);
      verify(audit).record(captor.capture());
      AuditRecordCommand cmd = captor.getValue();
      assertThat(cmd.action()).isEqualTo("work_record.template.schema.update");
      assertThat(cmd.detailJson()).contains("fieldCount");
    }

    @Test
    void fieldCreateWritesAudit() {
      WorkRecordField stored =
          new WorkRecordField(
              "f1",
              "t1",
              "tpl1",
              "memo",
              "memo",
              "text",
              false,
              null,
              "static",
              null,
              "[]",
              true,
              false,
              true,
              false,
              0,
              true,
              ".properties.memo",
              OffsetDateTime.now(),
              OffsetDateTime.now());
      when(fieldRepository.create(eq("t1"), eq("tpl1"), any(CreateFieldRequest.class)))
          .thenReturn(stored);

      templateService.createField(
          "t1",
          "tpl1",
          new CreateFieldRequest(
              "memo",
              "memo",
              "text",
              false,
              null,
              "static",
              null,
              "[]",
              true,
              false,
              true,
              false,
              0,
              true,
              ".properties.memo"),
          "admin");

      ArgumentCaptor<AuditRecordCommand> captor = ArgumentCaptor.forClass(AuditRecordCommand.class);
      verify(audit).record(captor.capture());
      AuditRecordCommand cmd = captor.getValue();
      assertThat(cmd.action()).isEqualTo("work_record.field.create");
      assertThat(cmd.targetType()).isEqualTo("wr_template_field");
      assertThat(cmd.detailJson()).contains("memo");
    }
  }

  @Nested
  class ExportAudit {

    @Test
    void exportWritesAuditWithRowCount() {
      UserPrincipal user = principal("u1", "work-record:export", "work-record:read:all");
      WorkRecord stored = sampleRecord("r1", "u1", "u1");
      when(repository.countWithFilters(
              eq("t1"), any(), any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(1L);
      when(repository.pageForExport(eq("t1"), anyInt(), any(), any(), any(), any(), any(), any()))
          .thenReturn(List.of(stored));

      byte[] csv = exportService.exportCsv("t1", null, null, null, null, null, null, null, user);

      assertThat(csv).isNotEmpty();
      ArgumentCaptor<AuditRecordCommand> captor = ArgumentCaptor.forClass(AuditRecordCommand.class);
      verify(audit).record(captor.capture());
      AuditRecordCommand cmd = captor.getValue();
      assertThat(cmd.action()).isEqualTo("work_record.record.export");
      assertThat(cmd.detailJson()).contains("\"count\":1");
      assertThat(cmd.detailJson()).contains("\"maxRows\":5000");
    }

    @Test
    void auditDoesNotStoreFullCustomData() {
      UserPrincipal user = principal("admin", "work-record:export", "work-record:read:all");
      WorkRecord stored = sampleRecord("r1", "u1", "u1");
      when(repository.countWithFilters(
              eq("t1"), any(), any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(1L);
      when(repository.pageForExport(eq("t1"), anyInt(), any(), any(), any(), any(), any(), any()))
          .thenReturn(List.of(stored));

      exportService.exportCsv("t1", null, null, null, null, null, null, null, user);

      ArgumentCaptor<AuditRecordCommand> captor = ArgumentCaptor.forClass(AuditRecordCommand.class);
      verify(audit).record(captor.capture());
      AuditRecordCommand cmd = captor.getValue();
      // 审计 detail 一定不能含 builtin/customDataJson 的原始 payload
      assertThat(cmd.detailJson()).doesNotContain("\"secret\"");
      assertThat(cmd.detailJson()).doesNotContain("\"customData\"");
    }
  }
}
