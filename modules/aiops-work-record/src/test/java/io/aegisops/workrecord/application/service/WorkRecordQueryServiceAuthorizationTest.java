package io.aegisops.workrecord.application.service;

import static io.aegisops.workrecord.support.WorkRecordFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.aegisops.common.api.PageResult;
import io.aegisops.workrecord.application.command.RecordDynamicFilter;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.port.WorkRecordCalendarPort;
import io.aegisops.workrecord.application.port.WorkRecordRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

/**
 * 查询服务授权与数据范围契约。
 *
 * <p>这些测试不依赖 Phase 编号，描述的是"长期不变的业务契约"。
 */
class WorkRecordQueryServiceAuthorizationTest {

  private final WorkRecordRepository repository = mock(WorkRecordRepository.class);

  private final WorkRecordDynamicFilterPolicyService filterPolicy =
      mock(WorkRecordDynamicFilterPolicyService.class);

  private final WorkRecordCalendarPort calendarPort = mock(WorkRecordCalendarPort.class);

  private final WorkRecordQueryService service =
      new WorkRecordQueryService(
          repository,
          new WorkRecordPermissionService(),
          filterPolicy,
          calendarPort,
          Clock.fixed(Instant.parse("2026-07-11T02:00:00Z"), ZoneId.of("Asia/Shanghai")));

  @Test
  void normalUserMustNotReadAnotherUsersRecord() {
    when(repository.find(TENANT_ID, "record-other"))
        .thenReturn(
            Optional.of(record("record-other", VERSION_ID, "user-other", "user-another", "{}")));

    assertThatThrownBy(() -> service.get(TENANT_ID, "record-other", normalUser()))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void normalUserListViewMustBeRestrictedToSelf() {
    when(repository.page(eq(TENANT_ID), any())).thenReturn(new PageResult<>(0L, 1, 20, List.of()));

    var result = service.page(TENANT_ID, query(null, List.of()), normalUser());

    ArgumentCaptor<RecordQuery> captor = ArgumentCaptor.forClass(RecordQuery.class);
    verify(repository).page(eq(TENANT_ID), captor.capture());

    assertThat(captor.getValue().onlySelf()).isTrue();
    assertThat(captor.getValue().currentUserId()).isEqualTo(NORMAL_USER_ID);
    assertThat(result.items()).isEmpty();
  }

  @Test
  void adminUserListViewMustNotForceOnlySelf() {
    when(repository.page(eq(TENANT_ID), any())).thenReturn(new PageResult<>(0L, 1, 20, List.of()));

    service.page(TENANT_ID, query(null, List.of()), adminUser());

    ArgumentCaptor<RecordQuery> captor = ArgumentCaptor.forClass(RecordQuery.class);
    verify(repository).page(eq(TENANT_ID), captor.capture());

    assertThat(captor.getValue().onlySelf()).isFalse();
    assertThat(captor.getValue().currentUserId()).isNull();
  }

  @Test
  void requestedVersionIdMustBePassedToFilterPolicy() {
    RecordDynamicFilter raw = RecordDynamicFilter.raw("priority", "eq", "P1");

    when(filterPolicy.normalize(TENANT_ID, TEMPLATE_ID, "version-1", List.of(raw)))
        .thenReturn(List.of(raw));

    RecordQuery input = query("version-1", List.of(raw));

    service.prepareEffectiveQuery(TENANT_ID, input, adminUser());

    verify(filterPolicy).normalize(TENANT_ID, TEMPLATE_ID, "version-1", List.of(raw));
  }

  @Test
  void userWithoutReadPermissionMustBeRejected() {
    var nobody =
        new io.aegisops.security.UserPrincipal(
            "user-nobody",
            TENANT_ID,
            "nobody",
            "无权限用户",
            java.util.Set.of("nobody"),
            java.util.Set.of(),
            java.util.Map.of());

    assertThatThrownBy(() -> service.page(TENANT_ID, query(null, List.of()), nobody))
        .isInstanceOf(AccessDeniedException.class);

    verifyNoInteractions(repository);
  }
}
