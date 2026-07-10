package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.aegisops.security.DataScope;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.port.WorkRecordCalendarPort;
import io.aegisops.workrecord.application.port.WorkRecordRepository;
import io.aegisops.workrecord.application.port.WorkRecordWorkMonth;
import io.aegisops.workrecord.application.port.WorkRecordWorkdayWindow;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkRecordQueryServicePhase15Test {
  private final WorkRecordRepository repository = mock(WorkRecordRepository.class);

  private final WorkRecordCalendarPort calendarPort = mock(WorkRecordCalendarPort.class);

  private final Clock clock =
      Clock.fixed(Instant.parse("2026-07-10T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

  private final WorkRecordQueryService service =
      new WorkRecordQueryService(
          repository,
          new WorkRecordPermissionService(),
          mock(WorkRecordDynamicFilterPolicyService.class),
          calendarPort,
          clock);

  @Test
  void recentWorkdaysShouldUseCalendarWindow() {
    when(calendarPort.recentWorkdays("t1", clock.instant(), 5))
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
        service.prepareEffectiveQuery("t1", query("recent_workdays", 5), admin());

    assertThat(effective.recordTimeFrom().toLocalDate()).isEqualTo(LocalDate.of(2026, 7, 4));

    assertThat(effective.recordTimeTo().toInstant()).isEqualTo(clock.instant());
  }

  @Test
  void thisWorkMonthShouldUseCalendarTimezoneAndRange() {
    when(calendarPort.currentWorkMonth("t1", clock.instant()))
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
        service.prepareEffectiveQuery("t1", query("this_work_month", null), admin());

    assertThat(effective.recordTimeFrom().toLocalDate()).isEqualTo(LocalDate.of(2026, 7, 1));

    assertThat(effective.recordTimeTo().toLocalDate()).isEqualTo(LocalDate.of(2026, 8, 1));
  }

  @Test
  void shouldRejectInvalidWorkdayCount() {
    assertThatThrownBy(
            () -> service.prepareEffectiveQuery("t1", query("recent_workdays", 61), admin()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 1 and 60");
  }

  private RecordQuery query(String quickView, Integer count) {
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
        count);
  }

  private UserPrincipal admin() {
    return new UserPrincipal(
        "u1",
        "t1",
        "alice",
        "Alice",
        Set.of("system_admin"),
        Set.of("work-record:read:all"),
        Map.of("work-record", DataScope.ALL));
  }
}
