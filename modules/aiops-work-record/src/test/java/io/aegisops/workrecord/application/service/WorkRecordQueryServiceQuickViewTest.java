package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.aegisops.common.api.PageResult;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.port.WorkRecordRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class WorkRecordQueryServiceQuickViewTest {
  private final WorkRecordRepository repository = mock(WorkRecordRepository.class);
  private final WorkRecordQueryService service =
      new WorkRecordQueryService(repository, new WorkRecordPermissionService());

  @Test
  void shouldApplyMineQuickViewWithOwnerOrCreatorScopeForAdmin() {
    Mockito.when(repository.page(eq("t1"), Mockito.any()))
        .thenReturn(new PageResult<>(0, 1, 20, List.of()));

    service.page("t1", query("mine"), user());

    ArgumentCaptor<RecordQuery> captor = ArgumentCaptor.forClass(RecordQuery.class);
    verify(repository).page(eq("t1"), captor.capture());

    assertThat(captor.getValue().onlySelf()).isTrue();
    assertThat(captor.getValue().currentUserId()).isEqualTo("u1");
    assertThat(captor.getValue().ownerId()).isNull();
    assertThat(captor.getValue().creatorId()).isNull();
  }

  @Test
  void shouldCalculateExactRecentFiveWorkdays() {
    Clock clock =
        Clock.fixed(
            Instant.parse("2026-07-13T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    WorkRecordQueryService fixedService =
        new WorkRecordQueryService(
            repository,
            new WorkRecordPermissionService(),
            Mockito.mock(WorkRecordDynamicFilterPolicyService.class),
            clock);

    Mockito.when(repository.page(eq("t1"), Mockito.any()))
        .thenReturn(new PageResult<>(0, 1, 20, List.of()));

    fixedService.page("t1", query("recent_workdays", 5), user());

    ArgumentCaptor<RecordQuery> captor = ArgumentCaptor.forClass(RecordQuery.class);
    verify(repository).page(eq("t1"), captor.capture());

    assertThat(captor.getValue().recordTimeFrom().toLocalDate())
        .isEqualTo(LocalDate.of(2026, 7, 7));
  }

  @Test
  void shouldForceOnlySelfForNonAdminUser() {
    Mockito.when(repository.page(eq("t1"), Mockito.any()))
        .thenReturn(new PageResult<>(0, 1, 20, List.of()));

    // 普通用户 (operator) 没有 read:all 权限, 必须强制只读自己的记录.
    service.page("t1", query("all"), selfUser());

    ArgumentCaptor<RecordQuery> captor = ArgumentCaptor.forClass(RecordQuery.class);
    verify(repository).page(eq("t1"), captor.capture());

    assertThat(captor.getValue().onlySelf()).isTrue();
    assertThat(captor.getValue().currentUserId()).isEqualTo("u2");
  }

  private RecordQuery query(String quickView) {
    return query(quickView, null);
  }

  private RecordQuery query(String quickView, Integer workdayCount) {
    return new RecordQuery(
        1,
        20,
        null,
        null,
        List.of(),
        null,
        null,
        null,
        null,
        null,
        false,
        null,
        List.of(),
        "recordTime",
        "desc",
        quickView,
        workdayCount);
  }

  private UserPrincipal user() {
    return new UserPrincipal(
        "u1",
        "t1",
        "alice",
        "Alice",
        Set.of("admin"));
  }

  private UserPrincipal selfUser() {
    return new UserPrincipal(
        "u2",
        "t1",
        "bob",
        "Bob",
        Set.of("operator"));
  }
}