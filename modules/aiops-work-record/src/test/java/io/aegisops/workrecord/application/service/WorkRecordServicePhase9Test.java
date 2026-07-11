package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.CreateRecordCommand;
import io.aegisops.workrecord.application.command.UpdateRecordCommand;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.application.port.WorkRecordRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateVersionRepository;
import io.aegisops.workrecord.application.port.WorkRecordUserPort;
import io.aegisops.workrecord.domain.model.RecordStatus;
import io.aegisops.workrecord.domain.model.WorkRecord;
import io.aegisops.workrecord.domain.model.WorkRecordTemplateVersion;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkRecordServicePhase9Test {
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
          new WorkRecordPayloadPolicy(new WorkRecordProductionProperties()),
          new ObjectMapper());

  @Test
  void createShouldRequireTemplateVersionId() {
    assertThatThrownBy(
            () ->
                service.create(
                    "t1",
                    new CreateRecordCommand(
                        "tpl1", null, "日报", "draft", null, OffsetDateTime.now(), "{}", "{}"),
                    user()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("templateVersionId is required");
  }

  @Test
  void createShouldRequireRecordTime() {
    assertThatThrownBy(
            () ->
                service.create(
                    "t1",
                    new CreateRecordCommand("tpl1", "v1", "日报", "draft", null, null, "{}", "{}"),
                    user()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("recordTime is required");
  }

  @Test
  void createShouldResolveVersionAndPersistSuccessfully() {
    WorkRecordTemplateVersion version =
        new WorkRecordTemplateVersion(
            "v1",
            "t1",
            "tpl1",
            1,
            "v1",
            "{}",
            "{}",
            "[]",
            "u1",
            OffsetDateTime.now(),
            OffsetDateTime.now());

    WorkRecord saved = stubRecord();

    when(versionRepository.findByTemplateAndVersion("t1", "tpl1", "v1"))
        .thenReturn(Optional.of(version));
    when(fieldRepository.listByVersion("t1", "v1")).thenReturn(List.of());
    when(recordRepository.create(eq("t1"), any(), eq("u1"))).thenReturn(saved);

    WorkRecord result =
        service.create(
            "t1",
            new CreateRecordCommand(
                "tpl1",
                "v1",
                "日报",
                "draft",
                "u1",
                OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                "{}",
                "{}"),
            user());

    assertThat(result.id()).isEqualTo("r1");

    ArgumentCaptor<CreateRecordCommand> commandCaptor =
        ArgumentCaptor.forClass(CreateRecordCommand.class);
    verify(recordRepository).create(eq("t1"), commandCaptor.capture(), eq("u1"));
    CreateRecordCommand sent = commandCaptor.getValue();
    assertThat(sent.templateVersionId()).isEqualTo("v1");
    assertThat(sent.status()).isEqualTo("draft");
    assertThat(sent.ownerId()).isEqualTo("u1");

    verify(versionRepository).findByTemplateAndVersion("t1", "tpl1", "v1");
    verify(fieldRepository).listByVersion("t1", "v1");
    verify(valueValidator).validate("t1", "v1", List.of(), "{}", false);
    verify(userPort).requireActiveUser("t1", "u1");
    verify(auditService)
        .recordChange(
            eq("t1"),
            eq("r1"),
            eq("tpl1"),
            eq("work_record"),
            eq("r1"),
            eq("work_record.record.create"),
            eq("u1"),
            any(),
            any(),
            any());
  }

  @Test
  void createShouldRejectVersionNotFound() {
    when(versionRepository.findByTemplateAndVersion("t1", "tpl1", "v2"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.create(
                    "t1",
                    new CreateRecordCommand(
                        "tpl1",
                        "v2",
                        "日报",
                        "draft",
                        null,
                        OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                        "{}",
                        "{}"),
                    user()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("template version not found");
  }

  @Test
  void updateShouldValidateOwnerWhenProvided() {
    assertThatThrownBy(
            () ->
                service.update(
                    "t1",
                    "r1",
                    new UpdateRecordCommand(
                        null, null, "u1", OffsetDateTime.parse("2026-01-01T00:00:00Z"), null, null),
                    user()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("work record not found");
  }

  private WorkRecord stubRecord() {
    return new WorkRecord(
        "r1",
        "t1",
        "tpl1",
        "v1",
        "日报",
        RecordStatus.DRAFT,
        "u1",
        "u1",
        OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        "{}",
        "{}",
        1,
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        null);
  }

  private UserPrincipal cachedUser;

  @org.junit.jupiter.api.BeforeEach
  void setUpUser() {
    cachedUser =
        new UserPrincipal(
            "u1",
            "t1",
            "alice",
            "Alice",
            Set.of("admin"),
            Set.of("work-record:write", "work-record:read:self"),
            java.util.Map.of());
  }

  private UserPrincipal user() {
    return cachedUser;
  }
}
