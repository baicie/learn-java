package io.aegisops.platform.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.aegisops.platform.audit.PlatformAuditService;
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
  private final PlatformAuditService audit = mock(PlatformAuditService.class);

  private final DefaultCalendarService service = new DefaultCalendarService(repository, audit);

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

    when(repository.listDefaultDays("t1", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 6)))
        .thenReturn(
            List.of(
                day(LocalDate.of(2026, 7, 1), "WORKDAY", true),
                day(LocalDate.of(2026, 7, 2), "WORKDAY", true),
                day(LocalDate.of(2026, 7, 3), "WORKDAY", true),
                day(LocalDate.of(2026, 7, 4), "ADJUSTED_WORKDAY", true),
                day(LocalDate.of(2026, 7, 5), "WEEKEND", false),
                day(LocalDate.of(2026, 7, 6), "WORKDAY", true)));

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
  void recentWorkdaysShouldRejectMissingCalendarDay() {
    CalendarRecord calendar = calendar(2026);

    when(repository.findDefaultCalendar("t1", 2026)).thenReturn(Optional.of(calendar));

    // 缺少 07-03，但 listRecentWorkdays 仍然返回了 5 条；这里必须被识别为不一致。
    when(repository.listRecentWorkdays("t1", LocalDate.of(2026, 7, 7), 5))
        .thenReturn(
            List.of(
                LocalDate.of(2026, 7, 7),
                LocalDate.of(2026, 7, 6),
                LocalDate.of(2026, 7, 4),
                LocalDate.of(2026, 7, 2),
                LocalDate.of(2026, 7, 1)));

    when(repository.listDefaultDays("t1", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 7)))
        .thenReturn(
            List.of(
                day(LocalDate.of(2026, 7, 1), "WORKDAY", true),
                day(LocalDate.of(2026, 7, 2), "WORKDAY", true),
                day(LocalDate.of(2026, 7, 4), "ADJUSTED_WORKDAY", true),
                day(LocalDate.of(2026, 7, 5), "WEEKEND", false),
                day(LocalDate.of(2026, 7, 6), "WORKDAY", true),
                day(LocalDate.of(2026, 7, 7), "WORKDAY", true)));

    assertThatThrownBy(() -> service.recentWorkdays("t1", Instant.parse("2026-07-07T04:00:00Z"), 5))
        .isInstanceOf(WorkCalendarConfigurationException.class)
        .hasMessageContaining("incomplete");
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
        .isInstanceOf(WorkCalendarConfigurationException.class)
        .hasMessageContaining("incomplete");
  }

  @Test
  void shouldRejectMissingDefaultCalendar() {
    when(repository.findDefaultCalendar("t1", 2026)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getDefaultCalendar("t1", 2026))
        .isInstanceOf(WorkCalendarConfigurationException.class)
        .hasMessageContaining("not configured");
  }

  @Test
  void setDefaultCalendarShouldAuditBeforeAndAfter() {
    CalendarRecord before = calendar("cal-old", 2026, true);
    CalendarRecord after = calendar("cal-new", 2026, true);

    when(repository.findCalendar("t1", "cal-new")).thenReturn(Optional.of(after));
    when(repository.findDefaultCalendar("t1", 2026)).thenReturn(Optional.of(before));
    when(repository.countCalendarDays(
            "t1", "cal-new", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
        .thenReturn(365);
    when(repository.setDefaultCalendar("t1", "cal-new", "u1")).thenReturn(after);

    CalendarRecord result = service.setDefaultCalendar("t1", "cal-new", "u1");

    assertThat(result.id()).isEqualTo("cal-new");

    verify(audit)
        .recordChange(
            eq("t1"),
            eq("u1"),
            eq("platform.calendar.default.change"),
            eq("platform_calendar_binding"),
            eq("t1:2026"),
            argThat(value -> value.toString().contains("cal-old")),
            argThat(value -> value.toString().contains("cal-new")),
            anyMap());
  }

  @Test
  void incompleteCalendarCannotBecomeDefault() {
    CalendarRecord target = calendar("cal-new", 2026, true);

    when(repository.findCalendar("t1", "cal-new")).thenReturn(Optional.of(target));

    when(repository.countCalendarDays(
            "t1", "cal-new", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
        .thenReturn(364);

    assertThatThrownBy(() -> service.setDefaultCalendar("t1", "cal-new", "u1"))
        .isInstanceOf(WorkCalendarConfigurationException.class)
        .hasMessageContaining("incomplete");

    verify(repository, never()).setDefaultCalendar(anyString(), anyString(), anyString());
    verifyNoInteractions(audit);
  }

  private CalendarRecord calendar(int year) {
    return calendar("cal-" + year, year, true);
  }

  private CalendarRecord calendar(String id, int year, boolean enabled) {
    OffsetDateTime now = OffsetDateTime.parse("2026-01-01T00:00:00Z");

    return new CalendarRecord(
        id,
        "t1",
        "CN_" + year,
        "中国大陆 " + year,
        "CN",
        "Asia/Shanghai",
        year,
        enabled,
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
