package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.CreateRecordCommand;
import io.aegisops.workrecord.application.command.UpdateRecordCommand;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.application.port.WorkRecordRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateVersionRepository;
import io.aegisops.workrecord.application.port.WorkRecordUserPort;
import io.aegisops.workrecord.domain.model.WorkRecordTemplateVersion;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

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
          permissionService,
          userPort,
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
                    new CreateRecordCommand(
                        "tpl1", "v1", "日报", "draft", null, null, "{}", "{}"),
                    user()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("recordTime is required");
  }

  @Test
  void createShouldResolveVersionByTenantTemplateAndVersion() {
    WorkRecordTemplateVersion version =
        new WorkRecordTemplateVersion(
            "v1", "t1", "tpl1", 1, "v1", "{}", "{}", "[]", "u1", OffsetDateTime.now(), OffsetDateTime.now());

    when(versionRepository.findByTemplateAndVersion("t1", "tpl1", "v1"))
        .thenReturn(Optional.of(version));
    when(fieldRepository.listByVersion("t1", "v1")).thenReturn(List.of());

    assertThatThrownBy(
            () ->
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
                    user()))
        .isInstanceOf(RuntimeException.class);

    verify(versionRepository).findByTemplateAndVersion("t1", "tpl1", "v1");
    verify(fieldRepository).listByVersion("t1", "v1");
    verify(valueValidator).validate("t1", "v1", List.of(), "{}");
    verify(userPort).requireActiveUser("t1", "u1");
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
                        null,
                        null,
                        "u1",
                        OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                        null,
                        null),
                    user()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("work record not found");
  }

  private UserPrincipal user() {
    return new UserPrincipal("u1", "t1", "alice", "Alice", Set.of("admin"));
  }
}