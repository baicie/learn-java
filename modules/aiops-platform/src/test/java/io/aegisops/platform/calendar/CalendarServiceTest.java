package io.aegisops.platform.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.audit.AuditService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CalendarServiceTest {

  private final CalendarRepository repository = Mockito.mock(CalendarRepository.class);
  private final AuditService audit = Mockito.mock(AuditService.class);
  private final CalendarService service = new CalendarService(repository, audit);

  @Test
  void shouldCreateCalendarAndAudit() {
    CalendarRecord record =
        new CalendarRecord(
            "cal-1",
            "tenant-1",
            "CN_2026",
            "中国大陆 2026 工作日历",
            "CN",
            "Asia/Shanghai",
            2026,
            true,
            "manual",
            null,
            "u1",
            OffsetDateTime.now(),
            OffsetDateTime.now());

    when(repository.createCalendar(any(), any(), any())).thenReturn(record);

    CalendarRecord created =
        service.createCalendar(
            "tenant-1",
            new CreateCalendarRequest(
                "CN_2026", "中国大陆 2026 工作日历", "CN", "Asia/Shanghai", 2026, true, "manual", null),
            "u1");

    assertThat(created.id()).isEqualTo("cal-1");
    verify(audit).record(any());
  }

  @Test
  void shouldRejectInvalidYear() {
    assertThatThrownBy(
            () ->
                service.createCalendar(
                    "tenant-1",
                    new CreateCalendarRequest(
                        "CN_1999", "bad", "CN", "Asia/Shanghai", 1999, true, "manual", null),
                    "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("year");
  }

  @Test
  void shouldReturnDerivedWorkdayWhenDayNotConfigured() {
    when(repository.findDay("tenant-1", "cal-1", LocalDate.of(2026, 7, 9)))
        .thenReturn(Optional.empty());

    WorkdayCheckResponse response =
        service.checkWorkday("tenant-1", "cal-1", LocalDate.of(2026, 7, 9));

    assertThat(response.workday()).isTrue();
    assertThat(response.dayType()).isEqualTo("WORKDAY");
  }

  @Test
  void shouldReturnConfiguredHoliday() {
    CalendarDayRecord day =
        new CalendarDayRecord(
            "day-1",
            "tenant-1",
            "cal-1",
            LocalDate.of(2026, 1, 1),
            4,
            "HOLIDAY",
            false,
            null,
            "元旦",
            "manual",
            null,
            "u1",
            OffsetDateTime.now(),
            OffsetDateTime.now());

    when(repository.findDay("tenant-1", "cal-1", LocalDate.of(2026, 1, 1)))
        .thenReturn(Optional.of(day));

    WorkdayCheckResponse response =
        service.checkWorkday("tenant-1", "cal-1", LocalDate.of(2026, 1, 1));

    assertThat(response.workday()).isFalse();
    assertThat(response.holidayName()).isEqualTo("元旦");
  }

  @Test
  void shouldCountDerivedWorkdaysWhenDaysAreMissing() {
    when(repository.listDays(
            "tenant-1", "cal-1", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12)))
        .thenReturn(java.util.List.of());

    WorkdayCountResponse response =
        service.countWorkdays(
            "tenant-1", "cal-1", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12));

    // 2026-07-06 (Mon) to 2026-07-12 (Sun) = 7 days
    // Mon-Fri are workdays, Sat-Sun are not
    assertThat(response.workdays()).isEqualTo(5);
  }

  @Test
  void shouldRejectNullCreateCalendarRequest() {
    assertThatThrownBy(() -> service.createCalendar("tenant-1", null, "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("calendar request is required");
  }
}
