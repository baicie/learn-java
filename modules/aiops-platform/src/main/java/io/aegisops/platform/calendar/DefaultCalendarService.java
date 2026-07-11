package io.aegisops.platform.calendar;

import io.aegisops.platform.audit.PlatformAuditService;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultCalendarService {
  private static final int MIN_YEAR = 2000;
  private static final int MAX_YEAR = 2100;
  private static final int MAX_RECENT_WORKDAYS = 60;

  private final DefaultCalendarRepository repository;
  private final PlatformAuditService audit;

  public DefaultCalendarService(DefaultCalendarRepository repository, PlatformAuditService audit) {
    this.repository = repository;
    this.audit = audit;
  }

  public CalendarRecord getDefaultCalendar(String tenantId, int year) {
    requireTenantId(tenantId);
    validateYear(year);

    return repository
        .findDefaultCalendar(tenantId, year)
        .orElseThrow(() -> WorkCalendarConfigurationException.defaultCalendarMissing(year));
  }

  @Transactional
  public CalendarRecord setDefaultCalendar(String tenantId, String calendarId, String actorId) {
    requireTenantId(tenantId);

    if (calendarId == null || calendarId.isBlank()) {
      throw new IllegalArgumentException("calendarId is required");
    }

    CalendarRecord target =
        repository
            .findCalendar(tenantId, calendarId)
            .orElseThrow(() -> new IllegalArgumentException("calendar not found"));

    if (!target.enabled()) {
      throw new IllegalStateException("disabled calendar cannot be default");
    }

    parseZone(target.timezone());
    validateCalendarCoverage(target);

    CalendarRecord before = repository.findDefaultCalendar(tenantId, target.year()).orElse(null);

    if (before != null && before.id().equals(target.id())) {
      return target;
    }

    CalendarRecord after = repository.setDefaultCalendar(tenantId, calendarId, actorId);

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("year", target.year());
    detail.put("previousCalendarId", before == null ? null : before.id());
    detail.put("currentCalendarId", after.id());

    audit.recordChange(
        tenantId,
        actorOrSystem(actorId),
        "platform.calendar.default.change",
        "platform_calendar_binding",
        tenantId + ":" + target.year(),
        snapshot(before),
        snapshot(after),
        detail);

    return after;
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
      throw WorkCalendarConfigurationException.incomplete(
          anchorDate.minusDays(Math.max(count * 3L, 30L)), anchorDate, count, descending.size());
    }

    List<LocalDate> ascending = new ArrayList<>(descending);
    Collections.reverse(ascending);

    LocalDate periodStart = ascending.getFirst();

    List<CalendarDayRecord> coverage =
        repository.listDefaultDays(tenantId, periodStart, anchorDate);

    validateContinuousCoverage(periodStart, anchorDate, coverage);

    List<LocalDate> actualWorkdays =
        coverage.stream().filter(day -> day.workday()).map(day -> day.calendarDate()).toList();

    if (!actualWorkdays.equals(ascending)) {
      throw WorkCalendarConfigurationException.inconsistentWorkdays(periodStart, anchorDate);
    }

    return new CalendarWorkdayWindow(current, anchorDate, periodStart, anchorDate, ascending);
  }

  public CalendarWorkMonth currentWorkMonth(String tenantId, Instant now) {
    requireTenantId(tenantId);

    if (now == null) {
      throw new IllegalArgumentException("now is required");
    }

    CalendarRecord current = resolveCurrentCalendar(tenantId, now);

    ZoneId zoneId = parseZone(current.timezone());

    YearMonth month = YearMonth.from(now.atZone(zoneId));

    return workMonthInternal(tenantId, month, current);
  }

  public CalendarWorkMonth workMonth(String tenantId, YearMonth month) {
    requireTenantId(tenantId);

    if (month == null) {
      throw new IllegalArgumentException("month is required");
    }

    CalendarRecord calendar = getDefaultCalendar(tenantId, month.getYear());

    return workMonthInternal(tenantId, month, calendar);
  }

  public boolean isWorkday(String tenantId, LocalDate date) {
    requireTenantId(tenantId);

    if (date == null) {
      throw new IllegalArgumentException("date is required");
    }

    getDefaultCalendar(tenantId, date.getYear());

    List<CalendarDayRecord> rows = repository.listDefaultDays(tenantId, date, date);

    validateContinuousCoverage(date, date, rows);

    return rows.getFirst().workday();
  }

  private CalendarWorkMonth workMonthInternal(
      String tenantId, YearMonth month, CalendarRecord calendar) {
    if (calendar.year() != month.getYear()) {
      throw new IllegalStateException("calendar year does not match month");
    }

    parseZone(calendar.timezone());

    LocalDate start = month.atDay(1);
    LocalDate end = month.atEndOfMonth();

    List<CalendarDayRecord> days = repository.listDefaultDays(tenantId, start, end);

    validateContinuousCoverage(start, end, days);

    List<LocalDate> workdays =
        days.stream().filter(day -> day.workday()).map(day -> day.calendarDate()).toList();

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

  private CalendarRecord resolveCurrentCalendar(String tenantId, Instant now) {
    int utcYear = now.atZone(ZoneOffset.UTC).getYear();

    int[] candidateYears = {utcYear, utcYear + 1, utcYear - 1};

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

    throw WorkCalendarConfigurationException.defaultCalendarMissing(utcYear);
  }

  private void validateCalendarCoverage(CalendarRecord calendar) {
    LocalDate start = LocalDate.of(calendar.year(), 1, 1);
    LocalDate end = LocalDate.of(calendar.year(), 12, 31);

    int expected =
        calendar.year() % 4 == 0 && (calendar.year() % 100 != 0 || calendar.year() % 400 == 0)
            ? 366
            : 365;

    int actual = repository.countCalendarDays(calendar.tenantId(), calendar.id(), start, end);

    if (actual != expected) {
      throw WorkCalendarConfigurationException.incomplete(start, end, expected, actual);
    }
  }

  private void validateContinuousCoverage(
      LocalDate start, LocalDate end, List<CalendarDayRecord> days) {
    int expected = Math.toIntExact(ChronoUnit.DAYS.between(start, end) + 1L);

    if (days.size() != expected) {
      throw WorkCalendarConfigurationException.incomplete(start, end, expected, days.size());
    }

    for (int index = 0; index < expected; index++) {
      LocalDate expectedDate = start.plusDays(index);
      LocalDate actualDate = days.get(index).calendarDate();

      if (!expectedDate.equals(actualDate)) {
        throw WorkCalendarConfigurationException.inconsistentWorkdays(start, end);
      }
    }
  }

  private Map<String, Object> snapshot(CalendarRecord calendar) {
    if (calendar == null) {
      return Map.of();
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", calendar.id());
    result.put("calendarCode", calendar.calendarCode());
    result.put("calendarName", calendar.calendarName());
    result.put("year", calendar.year());
    result.put("timezone", calendar.timezone());
    result.put("enabled", calendar.enabled());

    return result;
  }

  private ZoneId parseZone(String value) {
    try {
      return ZoneId.of(value);
    } catch (DateTimeException ex) {
      throw WorkCalendarConfigurationException.invalidTimezone(value);
    }
  }

  private void validateYear(int year) {
    if (year < MIN_YEAR || year > MAX_YEAR) {
      throw new IllegalArgumentException("year must be between " + MIN_YEAR + " and " + MAX_YEAR);
    }
  }

  private String actorOrSystem(String actorId) {
    return actorId == null || actorId.isBlank() ? "system" : actorId;
  }

  private void requireTenantId(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId is required");
    }
  }
}
