package io.aegisops.workrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.audit.AuditService;
import io.aegisops.common.api.PageResult;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.api.dto.UpdateWorkRecordRequest;
import io.aegisops.workrecord.application.WorkRecordApplicationService;
import io.aegisops.workrecord.application.WorkRecordExportApplicationService;
import io.aegisops.workrecord.domain.model.WorkRecord;
import io.aegisops.workrecord.infrastructure.config.WorkRecordProperties;
import io.aegisops.workrecord.infrastructure.persistence.WorkRecordFieldRepository;
import io.aegisops.workrecord.infrastructure.persistence.WorkRecordRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Phase 07 工作记录权限测试。
 *
 * <p>覆盖 read:self / read:all / write / delete / export 的边界。
 */
class WorkRecordPermissionTest {

  private WorkRecordRepository repository;
  private WorkRecordFieldRepository fieldRepository;
  private AuditService audit;
  private WorkRecordApplicationService service;

  @BeforeEach
  void setUp() {
    repository = mock(WorkRecordRepository.class);
    fieldRepository = mock(WorkRecordFieldRepository.class);
    audit = mock(AuditService.class);
    service = new WorkRecordApplicationService(repository, fieldRepository, audit);
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
  class ReadSelfScope {

    @Test
    void normalUserCannotReadOtherUsersRecord() {
      WorkRecord record = sampleRecord("r1", "owner-x", "creator-x");
      when(repository.find("t1", "r1")).thenReturn(Optional.of(record));
      UserPrincipal user = principal("u1", "work-record:read:self");

      assertThatThrownBy(() -> service.get("t1", "r1", user)).isInstanceOf(SecurityException.class);
    }

    @Test
    void normalUserCanReadOwnCreatedRecord() {
      WorkRecord record = sampleRecord("r1", "owner-x", "u1");
      when(repository.find("t1", "r1")).thenReturn(Optional.of(record));
      UserPrincipal user = principal("u1", "work-record:read:self");

      WorkRecord result = service.get("t1", "r1", user);

      assertThat(result.id()).isEqualTo("r1");
    }

    @Test
    void normalUserCanReadOwnedRecord() {
      WorkRecord record = sampleRecord("r1", "u1", "creator-x");
      when(repository.find("t1", "r1")).thenReturn(Optional.of(record));
      UserPrincipal user = principal("u1", "work-record:read:self");

      WorkRecord result = service.get("t1", "r1", user);

      assertThat(result.id()).isEqualTo("r1");
    }

    @Test
    void adminCanReadTenantRecords() {
      WorkRecord record = sampleRecord("r1", "owner-x", "creator-x");
      when(repository.find("t1", "r1")).thenReturn(Optional.of(record));
      UserPrincipal admin = principal("admin", "work-record:read:all", "work-record:read:self");

      WorkRecord result = service.get("t1", "r1", admin);

      assertThat(result.id()).isEqualTo("r1");
    }

    @Test
    void listWithoutReadAll_scopesToSelf() {
      UserPrincipal user = principal("u1", "work-record:read:self");
      when(repository.countForUser(eq("t1"), eq("u1"), any())).thenReturn(0L);
      when(repository.pageForUser(eq("t1"), eq("u1"), any(), anyInt(), anyInt()))
          .thenReturn(List.of());

      PageResult<WorkRecord> result = service.list("t1", user, null, 1, 20);

      assertThat(result.items()).isEmpty();
      verify(repository).pageForUser(eq("t1"), eq("u1"), any(), eq(1), eq(20));
      verify(repository, never()).page(eq("t1"), any(), any(), anyInt(), anyInt());
    }
  }

  @Nested
  class WriteScope {

    @Test
    void update_otherUsersRecord_denies() {
      WorkRecord record = sampleRecord("r1", "owner-x", "creator-x");
      when(repository.find("t1", "r1")).thenReturn(Optional.of(record));
      UserPrincipal user = principal("u1", "work-record:write", "work-record:read:self");

      assertThatThrownBy(
              () ->
                  service.update(
                      "t1",
                      "r1",
                      new UpdateWorkRecordRequest("新", null, null, null, null, null),
                      user))
          .isInstanceOf(SecurityException.class);
    }

    @Test
    void delete_otherUsersRecord_denies() {
      WorkRecord record = sampleRecord("r1", "owner-x", "creator-x");
      when(repository.find("t1", "r1")).thenReturn(Optional.of(record));
      UserPrincipal user = principal("u1", "work-record:delete", "work-record:read:self");

      assertThatThrownBy(() -> service.delete("t1", "r1", user))
          .isInstanceOf(SecurityException.class);
    }

    @Test
    void delete_ownRecord_succeeds() {
      WorkRecord record = sampleRecord("r1", "u1", "u1");
      when(repository.find("t1", "r1")).thenReturn(Optional.of(record));
      UserPrincipal user = principal("u1", "work-record:delete", "work-record:read:self");

      service.delete("t1", "r1", user);

      verify(repository).softDelete("t1", "r1");
    }

    @Test
    void export_withoutPermission_denies() {
      UserPrincipal user = principal("u1", "work-record:read:self");

      assertThatThrownBy(
              () ->
                  new WorkRecordExportApplicationService(
                          repository, fieldRepository, audit, new WorkRecordProperties())
                      .exportCsv("t1", null, null, null, null, null, null, null, user))
          .isInstanceOf(SecurityException.class);
    }

    @Test
    void export_withPermission_butOnlyReadSelf_scopesToSelf() {
      UserPrincipal user = principal("u1", "work-record:export", "work-record:read:self");
      when(repository.countForExportUser(
              eq("t1"), eq("u1"), any(), any(), any(), any(), any(), any()))
          .thenReturn(0L);
      when(repository.pageForExportUser(
              eq("t1"), eq("u1"), anyInt(), any(), any(), any(), any(), any(), any()))
          .thenReturn(List.of());

      byte[] csv =
          new WorkRecordExportApplicationService(
                  repository, fieldRepository, audit, new WorkRecordProperties())
              .exportCsv("t1", null, null, null, null, null, null, null, user);

      assertThat(csv).isNotEmpty();
      verify(repository)
          .pageForExportUser(
              eq("t1"), eq("u1"), anyInt(), any(), any(), any(), any(), any(), any());
    }
  }
}
