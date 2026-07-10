package io.aegisops.platform.calendar;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultCalendarService {
  private static final int MAX_RECENT_WORKDAYS = 60;

  private final DefaultCalendarRepository repository;

  public DefaultCalendarService(DefaultCalendarRepository repository) {
    this.repository = repository;
  }

  public CalendarRecord getDefaultCalendar(String tenantId, int year) {
    requireTenantId(tenantId);

    return repository
        .findDefaultCalendar(tenantId, year)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "default work calendar is not configured " + "for year " + year));
  }

  @Transactional
  public CalendarRecord setDefaultCalendar(String tenantId, String calendarId, String actorId) {
    requireTenantId(tenantId);

    if (calendarId == null || calendarId.isBlank()) {
      throw new IllegalArgumentException("calendarId is required");
    }

    return repository.setDefaultCalendar(tenantId, calendarId, actorId);
  }

  public CalendarWorkdayWindow recentWorkdays(String tenantId, Instant now, int count) {
    requireTenantId(tenantId);

    if (now == null) {
      throw new IllegalArgumentException("now is required");
    }

    if (count < 1 || count > MAX_RECENT_WORKDAYS) {
      throw new IllegalArgumentException(
          "workdayCount must be between 1 and " + MAX_RECENT_WORKDAYS);
    }

    CalendarRecord current = resolveCurrentCalendar(tenantId, now);

    ZoneId zoneId = parseZone(current.timezone());

    LocalDate anchorDate = now.atZone(zoneId).toLocalDate();

    List<LocalDate> descending = repository.listRecentWorkdays(tenantId, anchorDate, count);

    if (descending.size() != count) {
      throw new IllegalStateException(
          "work calendar data is incomplete: "
              + "expected "
              + count
              + " workdays but found "
              + descending.size());
    }

    List<LocalDate> ascending = new ArrayList<>(descending);

    Collections.reverse(ascending);

    return new CalendarWorkdayWindow(
        current, anchorDate, ascending.getFirst(), anchorDate, ascending);
  }

  public CalendarWorkMonth currentWorkMonth(String tenantId, Instant now) {
    CalendarRecord current = resolveCurrentCalendar(tenantId, now);

    ZoneId zoneId = parseZone(current.timezone());

    YearMonth month = YearMonth.from(now.atZone(zoneId));

    return workMonth(tenantId, month);
  }

  public CalendarWorkMonth workMonth(String tenantId, YearMonth month) {
    requireTenantId(tenantId);

    if (month == null) {
      throw new IllegalArgumentException("month is required");
    }

    CalendarRecord calendar = getDefaultCalendar(tenantId, month.getYear());

    LocalDate start = month.atDay(1);

    LocalDate end = month.atEndOfMonth();

    List<CalendarDayRecord> days = repository.listDefaultDays(tenantId, start, end);

    if (days.size() != month.lengthOfMonth()) {
      throw new IllegalStateException(
          "work calendar data is incomplete for "
              + month
              + ": expected "
              + month.lengthOfMonth()
              + " days but found "
              + days.size());
    }

    List<LocalDate> workdays =
        days.stream()
            .filter(CalendarDayRecord::workday)
            .map(CalendarDayRecord::calendarDate)
            .toList();

    return new CalendarWorkMonth(
        calendar,
        month,
        start,
        end,
        workdays.size(),
        workdays.isEmpty() ? null : workdays.getFirst(),
        workdays.isEmpty() ? null : workdays.getLast(),
        workdays);
  }

  public boolean isWorkday(String tenantId, LocalDate date) {
    requireTenantId(tenantId);

    if (date == null) {
      throw new IllegalArgumentException("date is required");
    }

    // 先校验该年度已经绑定默认日历。
    getDefaultCalendar(tenantId, date.getYear());

    List<CalendarDayRecord> rows = repository.listDefaultDays(tenantId, date, date);

    if (rows.size() != 1) {
      throw new IllegalStateException("work calendar day is missing: " + date);
    }

    return rows.getFirst().workday();
  }

  private CalendarRecord resolveCurrentCalendar(String tenantId, Instant now) {
    int utcYear = now.atZone(ZoneOffset.UTC).getYear();

    List<Integer> candidateYears = List.of(utcYear, utcYear - 1, utcYear + 1);

    for (int year : candidateYears) {
      CalendarRecord calendar = repository.findDefaultCalendar(tenantId, year).orElse(null);

      if (calendar == null) {
        continue;
      }

      ZoneId zoneId = parseZone(calendar.timezone());

      int localYear = now.atZone(zoneId).getYear();

      if (localYear == calendar.year()) {
        return calendar;
      }
    }

    throw new IllegalStateException(
        "default work calendar is not configured " + "for current local year");
  }

  private ZoneId parseZone(String value) {
    try {
      return ZoneId.of(value);
    } catch (DateTimeException ex) {
      throw new IllegalStateException("invalid calendar timezone: " + value, ex);
    }
  }

  private void requireTenantId(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId is required");
    }
  }
}
