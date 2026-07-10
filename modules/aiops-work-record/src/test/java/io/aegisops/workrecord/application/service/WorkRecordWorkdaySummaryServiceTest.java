package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.aegisops.workrecord.application.port.WorkRecordCalendarPort;
import io.aegisops.workrecord.application.port.WorkRecordWorkMonth;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkRecordWorkdaySummaryServiceTest {
  private final WorkRecordCalendarPort calendarPort = mock(WorkRecordCalendarPort.class);

  private final Clock clock =
      Clock.fixed(Instant.parse("2026-07-10T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

  private final WorkRecordWorkdaySummaryService service =
      new WorkRecordWorkdaySummaryService(calendarPort, clock);

  @Test
  void shouldReturnCurrentMonthWorkdayCount() {
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

    var result = service.currentMonth("t1");

    assertThat(result.month()).isEqualTo("2026-07");

    assertThat(result.workdayCount()).isEqualTo(23);

    assertThat(result.calendarId()).isEqualTo("cal1");
  }
}
