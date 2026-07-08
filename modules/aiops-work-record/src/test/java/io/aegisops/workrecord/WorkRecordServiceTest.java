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
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.api.dto.CreateWorkRecordRequest;
import io.aegisops.workrecord.api.dto.UpdateWorkRecordRequest;
import io.aegisops.workrecord.application.WorkRecordApplicationService;
import io.aegisops.workrecord.domain.model.WorkRecord;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.infrastructure.persistence.WorkRecordFieldRepository;
import io.aegisops.workrecord.infrastructure.persistence.WorkRecordRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class WorkRecordServiceTest {
  private final WorkRecordRepository repository = mock(WorkRecordRepository.class);
  private final WorkRecordFieldRepository fieldRepository = mock(WorkRecordFieldRepository.class);
  private final AuditService audit = mock(AuditService.class);
  private final WorkRecordApplicationService service =
      new WorkRecordApplicationService(repository, fieldRepository, audit);

  /**
   * Test-only principal factory. The {@code roles} set is unused by the service (it only inspects
   * {@link UserPrincipal#getAuthorities()}); we still pass one role so the principal is a valid
   * record value.
   */
  private static UserPrincipal principal(String id, String... authorities) {
    var auths =
        java.util.Arrays.stream(authorities)
            .<org.springframework.security.core.GrantedAuthority>map(SimpleGrantedAuthority::new)
            .collect(java.util.stream.Collectors.toUnmodifiableList());
    var principal = mock(UserPrincipal.class);
    when(principal.id()).thenReturn(id);
    doReturn(auths).when(principal).getAuthorities();
    return principal;
  }

  @Test
  void create_shouldValidateTemplateId() {
    assertThatThrownBy(
            () ->
                service.create(
                    "t1",
                    new CreateWorkRecordRequest("", "日报", "draft", null, null, "{}", "{}"),
                    "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("templateId");
  }

  @Test
  void create_shouldValidateTitle() {
    assertThatThrownBy(
            () ->
                service.create(
                    "t1",
                    new CreateWorkRecordRequest("tpl1", "", "draft", null, null, "{}", "{}"),
                    "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("title");
  }

  @Test
  void create_shouldRejectNullRequest() {
    assertThatThrownBy(() -> service.create("t1", null, "u1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void create_shouldRejectMalformedBuiltinJson() {
    assertThatThrownBy(
            () ->
                service.create(
                    "t1",
                    new CreateWorkRecordRequest(
                        "tpl1", "日报", "draft", null, null, "not-json", "{}"),
                    "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("builtinDataJson");
  }

  @Test
  void create_shouldRejectMalformedCustomJson() {
    assertThatThrownBy(
            () ->
                service.create(
                    "t1",
                    new CreateWorkRecordRequest("tpl1", "日报", "draft", null, null, "{}", "[]"),
                    "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be a JSON object");
  }

  @Test
  void list_shouldRejectUnsupportedStatus() {
    UserPrincipal user = principal("u1", "work-record:read:self");
    assertThatThrownBy(() -> service.list("t1", user, "closed", 1, 20))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported status");
  }

  @Test
  void create_shouldRejectUnsupportedStatus() {
    assertThatThrownBy(
            () ->
                service.create(
                    "t1",
                    new CreateWorkRecordRequest("tpl1", "日报", "closed", null, null, "{}", "{}"),
                    "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported status");
  }

  @Test
  void delete_shouldDelegateToRepositoryWithTenant() {
    WorkRecord record = sampleRecord("r1", "u1", "u1");
    when(repository.find("t1", "r1")).thenReturn(java.util.Optional.of(record));
    UserPrincipal user = principal("u1", "work-record:write", "work-record:read:all");

    service.delete("t1", "r1", user);

    verify(repository).softDelete("t1", "r1");
    verify(audit).record(any());
  }

  @Test
  void get_shouldRejectEmptyId() {
    assertThatThrownBy(() -> service.get("t1", "", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("id");
  }

  @Test
  void list_withoutReadAllAuthority_scopesToSelf() {
    UserPrincipal user = principal("u1", "work-record:read:self");
    when(repository.countForUser(eq("t1"), eq("u1"), any())).thenReturn(0L);
    when(repository.pageForUser(eq("t1"), eq("u1"), any(), anyInt(), anyInt()))
        .thenReturn(List.of());

    var result = service.list("t1", user, null, 1, 20);

    assertThat(result.items()).isEmpty();
    assertThat(result.total()).isZero();
    verify(repository).pageForUser(eq("t1"), eq("u1"), any(), eq(1), eq(20));
    verify(repository, never()).page(eq("t1"), any(), any(), anyInt(), anyInt());
  }

  @Test
  void list_withReadAllAuthority_returnsAll() {
    UserPrincipal user = principal("admin", "work-record:read:all", "work-record:read:self");
    when(repository.count(eq("t1"), eq(null), eq(null))).thenReturn(0L);
    when(repository.page(eq("t1"), eq(null), eq(null), anyInt(), anyInt())).thenReturn(List.of());

    var result = service.list("t1", user, null, null, null);

    assertThat(result.items()).isEmpty();
    verify(repository).page(eq("t1"), eq(null), eq(null), eq(1), eq(20));
  }

  @Test
  void list_sizeOver100_isClampedTo100() {
    UserPrincipal user = principal("admin", "work-record:read:all", "work-record:read:self");
    when(repository.count(eq("t1"), any(), any())).thenReturn(0L);
    when(repository.page(eq("t1"), any(), any(), anyInt(), anyInt())).thenReturn(List.of());

    service.list("t1", user, null, 1, 5000);

    verify(repository).page(eq("t1"), any(), any(), eq(1), eq(100));
  }

  @Test
  void get_withoutReadAll_andNotOwner_denies() {
    WorkRecord record = sampleRecord("r1", "owner-x", "creator-x");
    when(repository.find("t1", "r1")).thenReturn(java.util.Optional.of(record));
    UserPrincipal user = principal("u1", "work-record:read:self");

    assertThatThrownBy(() -> service.get("t1", "r1", user)).isInstanceOf(SecurityException.class);
  }

  @Test
  void get_withoutReadAll_butOwner_allows() {
    WorkRecord record = sampleRecord("r1", "u1", "u1");
    when(repository.find("t1", "r1")).thenReturn(java.util.Optional.of(record));
    UserPrincipal user = principal("u1", "work-record:read:self");

    var result = service.get("t1", "r1", user);

    assertThat(result.id()).isEqualTo("r1");
  }

  @Test
  void update_onlyTitle_keepsCustomJsonUntouched() {
    WorkRecord existing = sampleRecord("r1", "u1", "u1");
    when(repository.find("t1", "r1")).thenReturn(java.util.Optional.of(existing));
    when(repository.update(eq("t1"), eq("r1"), any(UpdateWorkRecordRequest.class)))
        .thenAnswer(
            inv -> {
              // 模拟 repository：保存 title，并把 customDataJson 留空(null)表示不动现有值
              UpdateWorkRecordRequest req = inv.getArgument(2);
              assertThat(req.title()).isEqualTo("新标题");
              assertThat(req.status()).isNull();
              assertThat(req.builtinDataJson()).isNull();
              assertThat(req.customDataJson()).isNull();
              return java.util.Optional.of(sampleRecord("r1", "u1", "u1"));
            });
    UserPrincipal user = principal("u1", "work-record:write", "work-record:read:self");

    var result =
        service.update(
            "t1", "r1", new UpdateWorkRecordRequest("新标题", null, null, null, null, null), user);

    assertThat(result.id()).isEqualTo("r1");
    verify(repository).update(eq("t1"), eq("r1"), any(UpdateWorkRecordRequest.class));
  }

  @Test
  void create_shouldPersistAndEmitAudit() {
    WorkRecord stored = sampleRecord("r1", "u1", "u1");
    when(fieldRepository.list(eq("t1"), eq("tpl1")))
        .thenReturn(
            List.of(
                new WorkRecordField(
                    "f1",
                    "t1",
                    "tpl1",
                    "巡检人",
                    "inspector",
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
                    ".properties.inspector",
                    OffsetDateTime.now(),
                    OffsetDateTime.now())));
    when(repository.create(any(), any(), eq("u1"))).thenReturn(stored);

    WorkRecord result =
        service.create(
            "t1",
            new CreateWorkRecordRequest(
                "tpl1", "日报", "draft", "u1", OffsetDateTime.now(), "{}", "{}"),
            "u1");

    assertThat(result.id()).isEqualTo("r1");
    verify(repository).create(eq("t1"), any(), eq("u1"));
    verify(audit).record(any());
  }

  @Test
  void create_withUnknownTemplate_throwsBadRequest() {
    when(fieldRepository.list(eq("t1"), eq("missing"))).thenReturn(List.of());

    assertThatThrownBy(
            () ->
                service.create(
                    "t1",
                    new CreateWorkRecordRequest(
                        "missing", "日报", "draft", "u1", OffsetDateTime.now(), "{}", "{}"),
                    "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("template not found");
  }

  private static WorkRecord sampleRecord(String id, String ownerId, String creatorId) {
    OffsetDateTime now = OffsetDateTime.now();
    return new WorkRecord(
        id, "t1", "tpl1", "标题", "draft", ownerId, creatorId, now, "{}", "{}", now, now);
  }
}
