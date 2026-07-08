package io.aegisops.workrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.audit.AuditService;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.WorkRecordExportApplicationService;
import io.aegisops.workrecord.domain.model.WorkRecord;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.infrastructure.config.WorkRecordProperties;
import io.aegisops.workrecord.infrastructure.persistence.WorkRecordFieldRepository;
import io.aegisops.workrecord.infrastructure.persistence.WorkRecordRepository;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class WorkRecordExportServiceTest {
  private static WorkRecordProperties propsWith(int maxRows) {
    WorkRecordProperties p = new WorkRecordProperties();
    p.getExport().setMaxRows(maxRows);
    return p;
  }

  private static WorkRecordProperties defaultProps() {
    return propsWith(5000);
  }

  @Test
  void exportCsv_shouldEscapeCommaAndQuote() {
    WorkRecordRepository repository = mock(WorkRecordRepository.class);
    WorkRecordFieldRepository fieldRepository = mock(WorkRecordFieldRepository.class);
    AuditService audit = mock(AuditService.class);
    when(repository.countWithFilters(any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(1L);
    when(repository.pageForExport(any(), anyInt(), any(), any(), any(), any(), any(), any()))
        .thenReturn(
            List.of(
                new WorkRecord(
                    "r1",
                    "t1",
                    "tpl1",
                    "巡检,\"核心\"",
                    "done",
                    "u2",
                    "u1",
                    OffsetDateTime.parse("2026-07-06T10:00:00Z"),
                    "{}",
                    "{}",
                    null,
                    null)));

    WorkRecordExportApplicationService service =
        new WorkRecordExportApplicationService(repository, fieldRepository, audit, defaultProps());
    UserPrincipal admin = principal("admin", "work-record:export", "work-record:read:all");
    String csv =
        new String(
            service.exportCsv("t1", null, null, null, null, null, null, null, admin),
            StandardCharsets.UTF_8);

    assertThat(csv).contains("\"巡检,\"\"核心\"\"\"");
    verify(audit).record(any());
  }

  @Test
  void exportCsv_shouldHandleEmptyList() {
    WorkRecordRepository repository = mock(WorkRecordRepository.class);
    WorkRecordFieldRepository fieldRepository = mock(WorkRecordFieldRepository.class);
    AuditService audit = mock(AuditService.class);
    when(repository.countWithFilters(any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(0L);
    when(repository.pageForExport(any(), anyInt(), any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of());

    WorkRecordExportApplicationService service =
        new WorkRecordExportApplicationService(repository, fieldRepository, audit, defaultProps());
    UserPrincipal admin = principal("admin", "work-record:export", "work-record:read:all");
    String csv =
        new String(
            service.exportCsv("t1", null, null, null, null, null, null, null, admin),
            StandardCharsets.UTF_8);

    assertThat(csv)
        .isEqualTo("id,title,status,templateId,ownerId,creatorId,recordTime,createdAt\n");
    verify(audit).record(any());
  }

  @Test
  void exportCsv_withoutReadAllAuthority_scopesToSelf() {
    WorkRecordRepository repository = mock(WorkRecordRepository.class);
    WorkRecordFieldRepository fieldRepository = mock(WorkRecordFieldRepository.class);
    AuditService audit = mock(AuditService.class);
    when(repository.countForExportUser(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(0L);
    when(repository.pageForExportUser(
            any(), any(), anyInt(), any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of());

    WorkRecordExportApplicationService service =
        new WorkRecordExportApplicationService(repository, fieldRepository, audit, defaultProps());
    UserPrincipal user = principal("u1", "work-record:export", "work-record:read:self");
    String csv =
        new String(
            service.exportCsv("t1", null, null, null, null, null, null, null, user),
            StandardCharsets.UTF_8);

    assertThat(csv)
        .isEqualTo("id,title,status,templateId,ownerId,creatorId,recordTime,createdAt\n");
    verify(repository)
        .countForExportUser(eq("t1"), eq("u1"), any(), any(), any(), any(), any(), any());
    verify(repository, never())
        .countWithFilters(any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(audit).record(any());
  }

  @Test
  void exportCsv_rejectsNonExportableColumn() {
    WorkRecordRepository repository = mock(WorkRecordRepository.class);
    WorkRecordFieldRepository fieldRepository = mock(WorkRecordFieldRepository.class);
    AuditService audit = mock(AuditService.class);
    WorkRecordField secret =
        new WorkRecordField(
            "f1",
            "t1",
            "tpl1",
            "secret",
            "secret",
            "text",
            false,
            null,
            "static",
            null,
            "[]",
            true,
            false,
            false, // exportable=false
            false,
            0,
            true,
            ".properties.secret",
            OffsetDateTime.now(),
            OffsetDateTime.now());
    when(fieldRepository.list(eq("t1"), eq("tpl1"))).thenReturn(List.of(secret));

    WorkRecordExportApplicationService service =
        new WorkRecordExportApplicationService(repository, fieldRepository, audit, defaultProps());
    UserPrincipal admin = principal("admin", "work-record:export", "work-record:read:all");

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                service.exportCsv(
                    "t1", "tpl1", null, null, null, null, null, List.of("id", "secret"), admin))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("secret");
  }

  @Test
  void exportCsv_rejectsColumnNotInTemplate() {
    WorkRecordRepository repository = mock(WorkRecordRepository.class);
    WorkRecordFieldRepository fieldRepository = mock(WorkRecordFieldRepository.class);
    AuditService audit = mock(AuditService.class);
    when(fieldRepository.list(eq("t1"), eq("tpl1"))).thenReturn(List.of());

    WorkRecordExportApplicationService service =
        new WorkRecordExportApplicationService(repository, fieldRepository, audit, defaultProps());
    UserPrincipal admin = principal("admin", "work-record:export", "work-record:read:all");

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                service.exportCsv(
                    "t1", "tpl1", null, null, null, null, null, List.of("id", "unknown"), admin))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown");
  }

  @Test
  void exportCsv_allowsBuiltinColumnAlways() {
    WorkRecordRepository repository = mock(WorkRecordRepository.class);
    WorkRecordFieldRepository fieldRepository = mock(WorkRecordFieldRepository.class);
    AuditService audit = mock(AuditService.class);
    when(repository.countWithFilters(any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(0L);
    when(repository.pageForExport(any(), anyInt(), any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of());

    WorkRecordExportApplicationService service =
        new WorkRecordExportApplicationService(repository, fieldRepository, audit, defaultProps());
    UserPrincipal admin = principal("admin", "work-record:export", "work-record:read:all");

    String csv =
        new String(
            service.exportCsv(
                "t1", null, null, null, null, null, null, List.of("id", "title", "status"), admin),
            StandardCharsets.UTF_8);
    assertThat(csv).isEqualTo("id,title,status\n");
  }

  @Test
  void maxRows_usesConfigurationProperty() {
    WorkRecordRepository repository = mock(WorkRecordRepository.class);
    WorkRecordFieldRepository fieldRepository = mock(WorkRecordFieldRepository.class);
    AuditService audit = mock(AuditService.class);
    WorkRecordExportApplicationService service =
        new WorkRecordExportApplicationService(repository, fieldRepository, audit, propsWith(2000));

    assertThat(service.getMaxExportRows()).isEqualTo(2000);
  }

  @Test
  void maxRows_fallsBackToDefaultWhenZeroOrNegative() {
    WorkRecordRepository repository = mock(WorkRecordRepository.class);
    WorkRecordFieldRepository fieldRepository = mock(WorkRecordFieldRepository.class);
    AuditService audit = mock(AuditService.class);
    WorkRecordExportApplicationService service =
        new WorkRecordExportApplicationService(repository, fieldRepository, audit, propsWith(0));

    assertThat(service.getMaxExportRows())
        .isEqualTo(WorkRecordExportApplicationService.DEFAULT_MAX_EXPORT_ROWS);
  }

  private static UserPrincipal principal(String id, String... authorities) {
    var auths =
        java.util.Arrays.stream(authorities)
            .<org.springframework.security.core.GrantedAuthority>map(SimpleGrantedAuthority::new)
            .collect(Collectors.toUnmodifiableList());
    var principal = mock(UserPrincipal.class);
    when(principal.id()).thenReturn(id);
    doReturn(auths).when(principal).getAuthorities();
    return principal;
  }
}
