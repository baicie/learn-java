package io.aegisops.platform.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.platform.audit.PlatformAuditService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CalendarServiceTest {

  private final CalendarRepository repository = mock(CalendarRepository.class);
  private final PlatformAuditService audit = mock(PlatformAuditService.class);
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
    verify(audit)
        .recordChange(
            eq("tenant-1"),
            anyString(),
            eq("platform.calendar.create"),
            eq("platform_calendar"),
            eq("cal-1"),
            any(),
            eq(record),
            any());
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

    assertThat(response.workdays()).isEqualTo(5);
  }

  @Test
  void shouldRejectNullCreateCalendarRequest() {
    assertThatThrownBy(() -> service.createCalendar("tenant-1", null, "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("calendar request is required");
  }

  @Test
  void updateDayShouldAuditBeforeAndAfter() {
    LocalDate date = LocalDate.of(2026, 10, 1);
    CalendarDayRecord before =
        new CalendarDayRecord(
            "day-1",
            "tenant-1",
            "cal-1",
            date,
            4,
            "WORKDAY",
            true,
            null,
            null,
            "generated",
            null,
            "system",
            OffsetDateTime.now(),
            OffsetDateTime.now());
    CalendarDayRecord after =
        new CalendarDayRecord(
            "day-1",
            "tenant-1",
            "cal-1",
            date,
            4,
            "HOLIDAY",
            false,
            null,
            "国庆节",
            "manual",
            null,
            "u1",
            OffsetDateTime.now(),
            OffsetDateTime.now());

    when(repository.findDay("tenant-1", "cal-1", date)).thenReturn(Optional.of(before));
    when(repository.upsertDay(
            eq("tenant-1"), eq("cal-1"), any(CalendarDayMutation.class), anyString()))
        .thenReturn(after);

    service.updateDay(
        "tenant-1",
        "cal-1",
        date,
        new UpdateCalendarDayRequest("HOLIDAY", false, null, "国庆节", "manual", null),
        "u1");

    verify(audit)
        .recordChange(
            eq("tenant-1"),
            eq("u1"),
            eq("platform.calendar.day.override"),
            eq("platform_calendar_day"),
            eq("day-1"),
            any(),
            any(),
            any());
  }

  @Test
  void importCsvShouldAuditOverwriteAndSummary() {
    LocalDate date = LocalDate.of(2026, 10, 1);

    CalendarDayRecord before =
        new CalendarDayRecord(
            "day-existing",
            "tenant-1",
            "cal-1",
            date,
            4,
            "WORKDAY",
            true,
            null,
            null,
            "generated",
            null,
            "system",
            OffsetDateTime.now(),
            OffsetDateTime.now());
    CalendarDayRecord after =
        new CalendarDayRecord(
            "day-existing",
            "tenant-1",
            "cal-1",
            date,
            4,
            "HOLIDAY",
            false,
            null,
            "国庆节",
            "csv",
            null,
            "u1",
            OffsetDateTime.now(),
            OffsetDateTime.now());

    CalendarRecord calendar =
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

    when(repository.findCalendar("tenant-1", "cal-1")).thenReturn(Optional.of(calendar));
    when(repository.findDay("tenant-1", "cal-1", date)).thenReturn(Optional.of(before));
    when(repository.upsertDay(
            eq("tenant-1"), eq("cal-1"), any(CalendarDayMutation.class), eq("u1")))
        .thenReturn(after);

    service.importCsv(
        "tenant-1",
        "cal-1",
        new ImportCalendarCsvRequest(
            """
            date,dayType,isWorkday,holidayName,remark
            2026-10-01,HOLIDAY,false,国庆节,
            """),
        "u1");

    ArgumentCaptor<String> actions = ArgumentCaptor.forClass(String.class);

    verify(audit, times(2))
        .recordChange(
            eq("tenant-1"),
            eq("u1"),
            actions.capture(),
            anyString(),
            anyString(),
            any(),
            any(),
            any());

    assertThat(actions.getAllValues())
        .containsExactlyInAnyOrder(
            "platform.calendar.day.import_overwrite", "platform.calendar.import");
  }

  @Test
  void importCsvShouldRejectTooManyRows() {
    when(repository.findCalendar("tenant-1", "cal-1"))
        .thenReturn(
            Optional.of(
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
                    OffsetDateTime.now())));

    StringBuilder csv = new StringBuilder("date,dayType,isWorkday,holidayName,remark\n");
    for (int i = 0; i < 1001; i++) {
      csv.append("2026-01-01,WORKDAY,true,,\n");
    }

    assertThatThrownBy(
            () ->
                service.importCsv(
                    "tenant-1", "cal-1", new ImportCalendarCsvRequest(csv.toString()), "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("1000");
  }

  @Test
  void createCalendarShouldRejectInvalidTimezone() {
    CreateCalendarRequest request =
        new CreateCalendarRequest(
            "CN_2026", "2026 工作日历", "CN", "Asia/Not-Exists", 2026, true, "manual", null);

    assertThatThrownBy(() -> service.createCalendar("tenant-1", request, "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid timezone");

    verify(repository, never()).createCalendar(any(), any(), any());
    verify(audit, never()).recordChange(any(), any(), any(), any(), any(), any(), any(), any());
  }
}
