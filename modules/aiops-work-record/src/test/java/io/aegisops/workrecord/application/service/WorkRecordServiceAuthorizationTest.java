package io.aegisops.workrecord.application.service;

import static io.aegisops.workrecord.support.WorkRecordFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.command.CreateRecordCommand;
import io.aegisops.workrecord.application.command.UpdateRecordCommand;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.application.port.WorkRecordRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateVersionRepository;
import io.aegisops.workrecord.application.port.WorkRecordUserPort;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.OptionSource;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/**
 * 服务层授权契约：覆盖普通用户越权、校验失败短路与审计边界。
 *
 * <p>这些测试不依赖 Phase 编号，描述的是"长期不变的业务契约"。
 */
class WorkRecordServiceAuthorizationTest {

  private final WorkRecordRepository recordRepository = mock(WorkRecordRepository.class);

  private final WorkRecordTemplateVersionRepository versionRepository =
      mock(WorkRecordTemplateVersionRepository.class);

  private final WorkRecordFieldIndexRepository fieldRepository =
      mock(WorkRecordFieldIndexRepository.class);

  private final WorkRecordValueValidator valueValidator = mock(WorkRecordValueValidator.class);

  private final WorkRecordAuditService auditService = mock(WorkRecordAuditService.class);

  private final WorkRecordPermissionService permissionService = new WorkRecordPermissionService();

  private final WorkRecordUserPort userPort = mock(WorkRecordUserPort.class);

  private final WorkRecordService service =
      new WorkRecordService(
          recordRepository,
          versionRepository,
          fieldRepository,
          valueValidator,
          auditService,
          new WorkRecordAuditSnapshots(new ObjectMapper()),
          permissionService,
          userPort,
          new ObjectMapper());

  @Test
  void normalUserMustNotUpdateAnotherUsersRecord() {
    when(recordRepository.find(TENANT_ID, "record-other"))
        .thenReturn(
            Optional.of(
                record(
                    "record-other",
                    VERSION_ID,
                    "user-other",
                    "user-another",
                    "{}")));

    assertThatThrownBy(
            () ->
                service.update(
                    TENANT_ID,
                    "record-other",
                    new UpdateRecordCommand("修改", null, null, null, null, null),
                    normalUser()))
        .isInstanceOf(AccessDeniedException.class);

    verify(recordRepository, never()).update(anyString(), anyString(), any());
    verifyNoInteractions(auditService);
  }

  @Test
  void validationFailureMustStopPersistenceAndAudit() {
    when(versionRepository.findByTemplateAndVersion(TENANT_ID, TEMPLATE_ID, VERSION_ID))
        .thenReturn(Optional.of(version(VERSION_ID)));

    var priority =
        field(
            VERSION_ID,
            "priority",
            FieldType.SELECT,
            OptionSource.DICT,
            "priority",
            "[]",
            false,
            true,
            true,
            true);

    when(fieldRepository.listByVersion(TENANT_ID, VERSION_ID)).thenReturn(List.of(priority));

    doThrow(
            new IllegalArgumentException("dict item not found or disabled: priority/P2"))
        .when(valueValidator)
        .validate(TENANT_ID, VERSION_ID, List.of(priority), "{\"priority\":\"P2\"}");

    assertThatThrownBy(
            () ->
                service.create(
                    TENANT_ID,
                    new CreateRecordCommand(
                        TEMPLATE_ID,
                        VERSION_ID,
                        "日报",
                        "done",
                        null,
                        NOW,
                        "{}",
                        "{\"priority\":\"P2\"}"),
                    adminUser()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not found or disabled");

    verify(recordRepository, never()).create(anyString(), any(), anyString());
    verifyNoInteractions(auditService);
  }

  @Test
  void adminUserWithAllScopeCanUpdateAnyRecord() {
    var existing = record("record-1", VERSION_ID, ADMIN_USER_ID, ADMIN_USER_ID, "{}");

    when(recordRepository.find(TENANT_ID, "record-1")).thenReturn(Optional.of(existing));
    when(recordRepository.update(eq(TENANT_ID), eq("record-1"), any()))
        .thenAnswer(
            invocation -> {
              UpdateRecordCommand cmd = invocation.getArgument(2);
              return new WorkRecord(
                  existing.id(),
                  existing.tenantId(),
                  existing.templateId(),
                  existing.templateVersionId(),
                  cmd.title(),
                  existing.status(),
                  existing.ownerId(),
                  existing.creatorId(),
                  existing.recordTime(),
                  existing.builtinDataJson(),
                  existing.customDataJson(),
                  existing.rowVersion() + 1,
                  existing.createdAt(),
                  NOW,
                  existing.deletedAt());
            });

    var result =
        service.update(
            TENANT_ID,
            "record-1",
            new UpdateRecordCommand(
                "新标题", null, null, NOW, "{}", existing.customDataJson()),
            adminUser());

    assertThat(result).isNotNull();
    assertThat(result.title()).isEqualTo("新标题");
    verify(auditService, times(1))
        .recordChange(
            eq(TENANT_ID),
            eq("record-1"),
            eq(TEMPLATE_ID),
            eq("work_record"),
            eq("record-1"),
            eq(WorkRecordAuditActions.RECORD_UPDATE),
            eq(ADMIN_USER_ID),
            any(),
            any(),
            any());
  }

  @Test
  void readonlyUserCannotCreateRecord() {
    assertThatThrownBy(
            () ->
                service.create(
                    TENANT_ID,
                    new CreateRecordCommand(
                        TEMPLATE_ID,
                        VERSION_ID,
                        "日报",
                        "done",
                        null,
                        NOW,
                        "{}",
                        "{}"),
                    readonlyUser()))
        .isInstanceOf(AccessDeniedException.class);

    verifyNoInteractions(recordRepository);
    verifyNoInteractions(auditService);
  }
}