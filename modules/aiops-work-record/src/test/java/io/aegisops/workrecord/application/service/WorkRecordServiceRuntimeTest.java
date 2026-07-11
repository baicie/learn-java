package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.CreateRecordCommand;
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

class WorkRecordServiceRuntimeTest {
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
  void shouldCreateRecordWithExplicitTemplateVersionAndValidateCustomData() {
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

    WorkRecord saved =
        new WorkRecord(
            "r1",
            "t1",
            "tpl1",
            "v1",
            "日报",
            RecordStatus.DRAFT,
            "u1",
            "u1",
            OffsetDateTime.now(),
            "{}",
            "{\"content\":\"hello\"}",
            1,
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            null);

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
                "{\"content\":\"hello\"}"),
            user("u1"));

    assertThat(result.id()).isEqualTo("r1");

    ArgumentCaptor<String> customJson = ArgumentCaptor.forClass(String.class);
    verify(valueValidator)
        .validate(eq("t1"), eq("v1"), eq(List.of()), customJson.capture(), eq(false));
    assertThat(customJson.getValue()).contains("content");
    verify(userPort).requireActiveUser("t1", "u1");
  }

  @Test
  void shouldResolveExplicitTemplateVersionByTenantTemplateAndVersion() {
    WorkRecordTemplateVersion version =
        new WorkRecordTemplateVersion(
            "v2",
            "t1",
            "tpl1",
            2,
            "v2",
            "{}",
            "{}",
            "[]",
            "u1",
            OffsetDateTime.now(),
            OffsetDateTime.now());

    WorkRecord saved =
        new WorkRecord(
            "r2",
            "t1",
            "tpl1",
            "v2",
            "周报",
            RecordStatus.DRAFT,
            null,
            "u1",
            OffsetDateTime.now(),
            "{}",
            "{}",
            1,
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            null);

    when(versionRepository.findByTemplateAndVersion("t1", "tpl1", "v2"))
        .thenReturn(Optional.of(version));
    when(fieldRepository.listByVersion("t1", "v2")).thenReturn(List.of());
    when(recordRepository.create(eq("t1"), any(), eq("u1"))).thenReturn(saved);

    WorkRecord result =
        service.create(
            "t1",
            new CreateRecordCommand(
                "tpl1",
                "v2",
                "周报",
                "draft",
                null,
                OffsetDateTime.parse("2026-02-01T00:00:00Z"),
                "{}",
                "{}"),
            user("u1"));

    assertThat(result.templateVersionId()).isEqualTo("v2");
  }

  @Test
  void shouldRejectCreateWithoutTemplateVersionId() {
    assertThatThrownBy(
            () ->
                service.create(
                    "t1",
                    new CreateRecordCommand(
                        "tpl1",
                        null,
                        "日报",
                        "draft",
                        null,
                        OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                        "{}",
                        "{}"),
                    user("u1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("templateVersionId is required");
  }

  @Test
  void shouldRejectCreateWhenVersionNotFound() {
    when(versionRepository.findByTemplateAndVersion("t1", "tpl1", "missing"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.create(
                    "t1",
                    new CreateRecordCommand(
                        "tpl1",
                        "missing",
                        "日报",
                        "draft",
                        null,
                        OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                        "{}",
                        "{}"),
                    user("u1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("template version not found");
  }

  private UserPrincipal cachedUser;

  @org.junit.jupiter.api.BeforeEach
  void setUpUser() {
    cachedUser =
        new UserPrincipal(
            "u1",
            "t1",
            "u1",
            "u1",
            Set.of("admin"),
            Set.of("work-record:write", "work-record:read:self"),
            java.util.Map.of());
  }

  private UserPrincipal user(String id) {
    return cachedUser;
  }
}
