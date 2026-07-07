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
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class WorkRecordExportServiceTest {
  @Test
  void exportCsv_shouldEscapeCommaAndQuote() {
    WorkRecordRepository repository = mock(WorkRecordRepository.class);
    AuditService audit = mock(AuditService.class);
    when(repository.export(eq("t1"), eq(null), eq(null), anyInt()))
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

    WorkRecordExportService service = new WorkRecordExportService(repository, audit);
    UserPrincipal admin = principal("admin", "work-record:export", "work-record:read:all");
    String csv = new String(service.exportCsv("t1", null, null, admin), StandardCharsets.UTF_8);

    assertThat(csv).startsWith("id,title,status,ownerId,creatorId,recordTime");
    assertThat(csv).contains("\"巡检,\"\"核心\"\"\"");
    verify(audit).record(any());
  }

  @Test
  void exportCsv_shouldHandleEmptyList() {
    WorkRecordRepository repository = mock(WorkRecordRepository.class);
    AuditService audit = mock(AuditService.class);
    when(repository.export(eq("t1"), eq(null), eq(null), anyInt())).thenReturn(List.of());

    WorkRecordExportService service = new WorkRecordExportService(repository, audit);
    UserPrincipal admin = principal("admin", "work-record:export", "work-record:read:all");
    String csv = new String(service.exportCsv("t1", null, null, admin), StandardCharsets.UTF_8);

    assertThat(csv).isEqualTo("id,title,status,ownerId,creatorId,recordTime\n");
    verify(audit).record(any());
  }

  @Test
  void exportCsv_withoutReadAllAuthority_scopesToSelf() {
    WorkRecordRepository repository = mock(WorkRecordRepository.class);
    AuditService audit = mock(AuditService.class);
    when(repository.exportForUser(eq("t1"), eq("u1"), eq(null), anyInt())).thenReturn(List.of());

    WorkRecordExportService service = new WorkRecordExportService(repository, audit);
    UserPrincipal user = principal("u1", "work-record:export", "work-record:read:self");
    String csv = new String(service.exportCsv("t1", null, null, user), StandardCharsets.UTF_8);

    assertThat(csv).isEqualTo("id,title,status,ownerId,creatorId,recordTime\n");
    verify(repository)
        .exportForUser(eq("t1"), eq("u1"), eq(null), eq(WorkRecordExportService.MAX_EXPORT_ROWS));
    verify(repository, never()).export(any(), any(), any(), anyInt());
    verify(audit).record(any());
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
