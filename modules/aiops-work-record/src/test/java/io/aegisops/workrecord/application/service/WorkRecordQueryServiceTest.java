package io.aegisops.workrecord.application.service;

import static io.aegisops.workrecord.support.WorkRecordFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.aegisops.common.api.PageResult;
import io.aegisops.workrecord.application.command.DynamicFilterOperator;
import io.aegisops.workrecord.application.command.RecordDynamicFilter;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.port.WorkRecordCalendarPort;
import io.aegisops.workrecord.application.port.WorkRecordRepository;
import io.aegisops.workrecord.application.port.WorkRecordWorkMonth;
import io.aegisops.workrecord.application.port.WorkRecordWorkdayWindow;
import io.aegisops.workrecord.domain.model.FieldType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 查询服务契约：覆盖过滤策略归一化、模板版本透传、日历视图与工作日窗口。
 *
 * <p>测试名描述长期不变的业务契约。
 */
class WorkRecordQueryServiceTest {

  private final WorkRecordRepository repository = mock(WorkRecordRepository.class);

  private final WorkRecordDynamicFilterPolicyService filterPolicy =
      mock(WorkRecordDynamicFilterPolicyService.class);

  private final WorkRecordCalendarPort calendarPort = mock(WorkRecordCalendarPort.class);

  private final Clock clock =
      Clock.fixed(Instant.parse("2026-07-10T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

  private final WorkRecordQueryService service =
      new WorkRecordQueryService(
          repository, new WorkRecordPermissionService(), filterPolicy, calendarPort, clock);

  @Test
  void dynamicFiltersMustBeNormalizedBeforeRepositoryPage() {
    RecordDynamicFilter raw = RecordDynamicFilter.raw("priority", "eq", "P1");
    RecordDynamicFilter normalized =
        RecordDynamicFilter.normalized(
            "priority", DynamicFilterOperator.EQ, FieldType.SELECT, "P1", List.of());

    when(filterPolicy.normalize(eq(TENANT_ID), eq(TEMPLATE_ID), eq(null), eq(List.of(raw))))
        .thenReturn(List.of(normalized));
    when(repository.page(eq(TENANT_ID), any())).thenReturn(new PageResult<>(0L, 1, 20, List.of()));

    service.page(TENANT_ID, baseQuery(null, List.of(raw)), adminUser());

    ArgumentCaptor<RecordQuery> captor = ArgumentCaptor.forClass(RecordQuery.class);
    verify(repository).page(eq(TENANT_ID), captor.capture());

    assertThat(captor.getValue().dynamicFilters()).containsExactly(normalized);
  }

  @Test
  void explicitTemplateVersionMustReachDynamicFilterPolicy() {
    RecordDynamicFilter raw = RecordDynamicFilter.raw("priority", "eq", "P1");
    RecordDynamicFilter normalized =
        RecordDynamicFilter.normalized(
            "priority", DynamicFilterOperator.EQ, FieldType.SELECT, "P1", List.of());

    when(filterPolicy.normalize(eq(TENANT_ID), eq(TEMPLATE_ID), eq("v1"), eq(List.of(raw))))
        .thenReturn(List.of(normalized));
    when(repository.page(eq(TENANT_ID), any())).thenReturn(new PageResult<>(0L, 1, 20, List.of()));

    service.page(TENANT_ID, baseQuery("v1", List.of(raw)), adminUser());

    verify(filterPolicy).normalize(eq(TENANT_ID), eq(TEMPLATE_ID), eq("v1"), eq(List.of(raw)));
  }

  @Test
  void recentWorkdaysViewMustUseCalendarWindow() {
    when(calendarPort.recentWorkdays(TENANT_ID, clock.instant(), 5))
        .thenReturn(
            new WorkRecordWorkdayWindow(
                "cal1",
                "中国大陆 2026",
                "Asia/Shanghai",
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 4),
                LocalDate.of(2026, 7, 10),
                List.of(
                    LocalDate.of(2026, 7, 4),
                    LocalDate.of(2026, 7, 6),
                    LocalDate.of(2026, 7, 8),
                    LocalDate.of(2026, 7, 9),
                    LocalDate.of(2026, 7, 10))));

    RecordQuery effective =
        service.prepareEffectiveQuery(TENANT_ID, quickViewQuery("recent_workdays", 5), adminUser());

    assertThat(effective.recordTimeFrom().toLocalDate()).isEqualTo(LocalDate.of(2026, 7, 4));
    assertThat(effective.recordTimeTo().toInstant()).isEqualTo(clock.instant());
  }

  @Test
  void thisWorkMonthViewMustUseCalendarTimezone() {
    when(calendarPort.currentWorkMonth(TENANT_ID, clock.instant()))
        .thenReturn(
            new WorkRecordWorkMonth(
                "cal1",
                "中国大陆 2026",
                "Asia/Shanghai",
                YearMonth.of(2026, 7),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                23,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                List.of()));

    RecordQuery effective =
        service.prepareEffectiveQuery(
            TENANT_ID, quickViewQuery("this_work_month", null), adminUser());

    assertThat(effective.recordTimeFrom().toLocalDate()).isEqualTo(LocalDate.of(2026, 7, 1));
    assertThat(effective.recordTimeTo().toLocalDate()).isEqualTo(LocalDate.of(2026, 8, 1));
  }

  @Test
  void workdayCountMustBeWithinAllowedRange() {
    assertThatThrownBy(
            () ->
                service.prepareEffectiveQuery(
                    TENANT_ID, quickViewQuery("recent_workdays", 61), adminUser()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 1 and 60");
  }

  private RecordQuery baseQuery(String versionId, List<RecordDynamicFilter> filters) {
    return new RecordQuery(
        1,
        20,
        TEMPLATE_ID,
        versionId,
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

  private RecordQuery quickViewQuery(String quickView, Integer workdayCount) {
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
}
