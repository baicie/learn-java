package io.aegisops.platform.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DefaultCalendarServiceTest {
  private final DefaultCalendarRepository repository = mock(DefaultCalendarRepository.class);

  private final DefaultCalendarService service = new DefaultCalendarService(repository);

  @Test
  void recentWorkdaysShouldUseCalendarFacts() {
    CalendarRecord calendar = calendar(2026);

    when(repository.findDefaultCalendar("t1", 2026)).thenReturn(Optional.of(calendar));

    when(repository.listRecentWorkdays("t1", LocalDate.of(2026, 7, 6), 5))
        .thenReturn(
            List.of(
                LocalDate.of(2026, 7, 6),
                LocalDate.of(2026, 7, 4),
                LocalDate.of(2026, 7, 3),
                LocalDate.of(2026, 7, 2),
                LocalDate.of(2026, 7, 1)));

    CalendarWorkdayWindow result =
        service.recentWorkdays("t1", Instant.parse("2026-07-06T04:00:00Z"), 5);

    assertThat(result.periodStart()).isEqualTo(LocalDate.of(2026, 7, 1));

    assertThat(result.workdays())
        .containsExactly(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 2),
            LocalDate.of(2026, 7, 3),
            // 调休周六由日历标记为工作日。
            LocalDate.of(2026, 7, 4),
            LocalDate.of(2026, 7, 6));
  }

  @Test
  void workMonthShouldCountAdjustedWorkdayAndExcludeHoliday() {
    YearMonth month = YearMonth.of(2026, 7);

    CalendarRecord calendar = calendar(2026);

    when(repository.findDefaultCalendar("t1", 2026)).thenReturn(Optional.of(calendar));

    List<CalendarDayRecord> days =
        IntStream.rangeClosed(1, month.lengthOfMonth())
            .mapToObj(
                day -> {
                  LocalDate date = month.atDay(day);

                  boolean workday = date.getDayOfWeek().getValue() < 6;

                  String type = workday ? "WORKDAY" : "WEEKEND";

                  if (date.equals(LocalDate.of(2026, 7, 4))) {
                    workday = true;
                    type = "ADJUSTED_WORKDAY";
                  }

                  if (date.equals(LocalDate.of(2026, 7, 6))) {
                    workday = false;
                    type = "HOLIDAY";
                  }

                  return day(date, type, workday);
                })
            .toList();

    when(repository.listDefaultDays("t1", month.atDay(1), month.atEndOfMonth())).thenReturn(days);

    CalendarWorkMonth result = service.workMonth("t1", month);

    assertThat(result.workdays()).contains(LocalDate.of(2026, 7, 4));

    assertThat(result.workdays()).doesNotContain(LocalDate.of(2026, 7, 6));
  }

  @Test
  void workMonthShouldRejectIncompleteCalendar() {
    YearMonth month = YearMonth.of(2026, 7);

    when(repository.findDefaultCalendar("t1", 2026)).thenReturn(Optional.of(calendar(2026)));

    when(repository.listDefaultDays("t1", month.atDay(1), month.atEndOfMonth()))
        .thenReturn(List.of());

    assertThatThrownBy(() -> service.workMonth("t1", month))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("incomplete");
  }

  @Test
  void shouldRejectMissingDefaultCalendar() {
    when(repository.findDefaultCalendar("t1", 2026)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getDefaultCalendar("t1", 2026))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not configured");
  }

  private CalendarRecord calendar(int year) {
    OffsetDateTime now = OffsetDateTime.parse("2026-01-01T00:00:00Z");

    return new CalendarRecord(
        "cal-" + year,
        "t1",
        "CN_" + year,
        "中国大陆 " + year,
        "CN",
        "Asia/Shanghai",
        year,
        true,
        "manual",
        null,
        "u1",
        now,
        now);
  }

  private CalendarDayRecord day(LocalDate date, String type, boolean workday) {
    return new CalendarDayRecord(
        "day-" + date,
        "t1",
        "cal-2026",
        date,
        date.getDayOfWeek().getValue(),
        type,
        workday,
        null,
        null,
        "manual",
        null,
        "u1",
        OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        OffsetDateTime.parse("2026-01-01T00:00:00Z"));
  }
}
