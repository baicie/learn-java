package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.common.api.PageResult;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.DynamicFilterOperator;
import io.aegisops.workrecord.application.command.RecordDynamicFilter;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.port.WorkRecordRepository;
import io.aegisops.workrecord.domain.model.FieldType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** Phase 11 集成测试：验证 QueryService 调用 PolicyService.normalize， normalized filter 正确传递到 Repository。 */
class WorkRecordQueryServicePhase11Test {
  private final WorkRecordRepository repository = mock(WorkRecordRepository.class);
  private final WorkRecordPermissionService permissionService = new WorkRecordPermissionService();
  private final WorkRecordDynamicFilterPolicyService filterPolicyService =
      mock(WorkRecordDynamicFilterPolicyService.class);
  private final WorkRecordQueryService service =
      new WorkRecordQueryService(
          repository, permissionService, filterPolicyService, java.time.Clock.systemUTC());

  @Test
  void shouldNormalizeDynamicFiltersBeforeRepositoryPage() {
    RecordDynamicFilter raw = RecordDynamicFilter.raw("priority", "eq", "P1");
    RecordDynamicFilter normalized =
        RecordDynamicFilter.normalized(
            "priority", DynamicFilterOperator.EQ, FieldType.SELECT, "P1", List.of());

    when(filterPolicyService.normalize(eq("t1"), eq("tpl1"), eq(null), eq(List.of(raw))))
        .thenReturn(List.of(normalized));
    when(repository.page(eq("t1"), Mockito.any()))
        .thenReturn(new PageResult<>(0, 1, 20, List.of()));

    service.page("t1", query(List.of(raw)), user());

    ArgumentCaptor<RecordQuery> captor = ArgumentCaptor.forClass(RecordQuery.class);
    verify(repository).page(eq("t1"), captor.capture());

    assertThat(captor.getValue().dynamicFilters()).containsExactly(normalized);
  }

  @Test
  void shouldPassTemplateVersionToDynamicFilterPolicy() {
    RecordDynamicFilter raw = RecordDynamicFilter.raw("priority", "eq", "P1");
    RecordDynamicFilter normalized =
        RecordDynamicFilter.normalized(
            "priority", DynamicFilterOperator.EQ, FieldType.SELECT, "P1", List.of());

    when(filterPolicyService.normalize(eq("t1"), eq("tpl1"), eq("v1"), eq(List.of(raw))))
        .thenReturn(List.of(normalized));
    when(repository.page(eq("t1"), Mockito.any()))
        .thenReturn(new PageResult<>(0, 1, 20, List.of()));

    RecordQuery queryWithVersion =
        new RecordQuery(
            1,
            20,
            "tpl1",
            "v1",
            List.of(),
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            List.of(raw),
            "recordTime",
            "desc",
            "all",
            null);

    service.page("t1", queryWithVersion, user());

    verify(filterPolicyService).normalize(eq("t1"), eq("tpl1"), eq("v1"), eq(List.of(raw)));
  }

  private RecordQuery query(List<RecordDynamicFilter> filters) {
    return new RecordQuery(
        1,
        20,
        "tpl1",
        null,
        List.of(),
        null,
        null,
        null,
        null,
        null,
        false,
        null,
        filters,
        "recordTime",
        "desc",
        "all",
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
            Set.of("work-record:read:all"),
            java.util.Map.of("work-record", io.aegisops.security.DataScope.ALL));
  }

  private UserPrincipal user() {
    return cachedUser;
  }
}
