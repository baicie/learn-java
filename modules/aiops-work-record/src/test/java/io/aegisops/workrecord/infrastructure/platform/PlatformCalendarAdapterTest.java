package io.aegisops.workrecord.infrastructure.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.aegisops.platform.calendar.CalendarRecord;
import io.aegisops.platform.calendar.CalendarWorkdayWindow;
import io.aegisops.platform.calendar.DefaultCalendarService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlatformCalendarAdapterTest {
  private final DefaultCalendarService service = mock(DefaultCalendarService.class);

  private final PlatformCalendarAdapter adapter = new PlatformCalendarAdapter(service);

  @Test
  void shouldMapRecentWorkdayWindow() {
    Instant now = Instant.parse("2026-07-10T02:00:00Z");

    when(service.recentWorkdays("t1", now, 5))
        .thenReturn(
            new CalendarWorkdayWindow(
                calendar(),
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 6),
                LocalDate.of(2026, 7, 10),
                List.of(
                    LocalDate.of(2026, 7, 6),
                    LocalDate.of(2026, 7, 7),
                    LocalDate.of(2026, 7, 8),
                    LocalDate.of(2026, 7, 9),
                    LocalDate.of(2026, 7, 10))));

    var result = adapter.recentWorkdays("t1", now, 5);

    assertThat(result.calendarId()).isEqualTo("cal1");

    assertThat(result.timeZone()).isEqualTo("Asia/Shanghai");

    assertThat(result.workdays()).hasSize(5);
  }

  private CalendarRecord calendar() {
    OffsetDateTime time = OffsetDateTime.parse("2026-01-01T00:00:00Z");

    return new CalendarRecord(
        "cal1",
        "t1",
        "CN_2026",
        "中国大陆 2026",
        "CN",
        "Asia/Shanghai",
        2026,
        true,
        "manual",
        null,
        "u1",
        time,
        time);
  }
}
