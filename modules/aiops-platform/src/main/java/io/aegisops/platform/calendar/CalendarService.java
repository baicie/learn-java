package io.aegisops.platform.calendar;

import io.aegisops.audit.AuditRecordCommand;
import io.aegisops.audit.AuditService;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarService {
  private final CalendarRepository repository;
  private final AuditService audit;
  private final CalendarCsvImporter csvImporter = new CalendarCsvImporter();

  public CalendarService(CalendarRepository repository, AuditService audit) {
    this.repository = repository;
    this.audit = audit;
  }

  public List<CalendarRecord> listCalendars(String tenantId) {
    return repository.listCalendars(tenantId);
  }

  @Transactional
  public CalendarRecord createCalendar(
      String tenantId, CreateCalendarRequest request, String actor) {
    if (request == null) {
      throw new IllegalArgumentException("calendar request is required");
    }
    requireText(request.calendarCode(), "calendarCode");
    requireText(request.calendarName(), "calendarName");
    if (request.year() == null || request.year() < 2000 || request.year() > 2100) {
      throw new IllegalArgumentException("year must be between 2000 and 2100");
    }

    CalendarRecord record = repository.createCalendar(tenantId, request, defaultActor(actor));
    initializeYearDays(tenantId, record.id(), request.year(), defaultActor(actor));

    audit.record(
        new AuditRecordCommand(
            tenantId,
            defaultActor(actor),
            "platform.calendar.create",
            "platform_calendar",
            record.id(),
            detail("calendarCode", record.calendarCode(), "year", String.valueOf(record.year()))));
    return record;
  }

  public List<CalendarDayRecord> listDays(
      String tenantId, String calendarId, LocalDate start, LocalDate end) {
    requireText(calendarId, "calendarId");
    requireRange(start, end);

    List<CalendarDayRecord> configured = repository.listDays(tenantId, calendarId, start, end);
    Map<LocalDate, CalendarDayRecord> byDate = new HashMap<>();
    for (CalendarDayRecord day : configured) {
      byDate.put(day.calendarDate(), day);
    }

    return start
        .datesUntil(end.plusDays(1))
        .map(date -> byDate.getOrDefault(date, derivedDay(tenantId, calendarId, date)))
        .toList();
  }

  @Transactional
  public CalendarDayRecord updateDay(
      String tenantId,
      String calendarId,
      LocalDate date,
      UpdateCalendarDayRequest request,
      String actor) {
    requireText(calendarId, "calendarId");
    if (date == null) {
      throw new IllegalArgumentException("date is required");
    }
    if (request == null) {
      throw new IllegalArgumentException("calendar day request is required");
    }
    requireText(request.dayType(), "dayType");
    validateDayType(request.dayType());
    if (request.workday() == null) {
      throw new IllegalArgumentException("workday is required");
    }

    CalendarDayRecord record =
        repository.upsertDay(
            tenantId,
            calendarId,
            date,
            request.dayType(),
            request.workday(),
            request.holidayCode(),
            request.holidayName(),
            request.sourceType(),
            request.remark(),
            defaultActor(actor));

    audit.record(
        new AuditRecordCommand(
            tenantId,
            defaultActor(actor),
            "platform.calendar.day.update",
            "platform_calendar_day",
            record.id(),
            detail(
                "calendarId",
                calendarId,
                "date",
                date.toString(),
                "workday",
                String.valueOf(record.workday()))));
    return record;
  }

  @Transactional
  public int importCsv(
      String tenantId, String calendarId, ImportCalendarCsvRequest request, String actor) {
    requireText(calendarId, "calendarId");
    if (request == null) {
      throw new IllegalArgumentException("calendar import request is required");
    }

    List<CalendarCsvImporter.CalendarCsvRow> rows = csvImporter.parse(request.csv());

    for (CalendarCsvImporter.CalendarCsvRow row : rows) {
      repository.upsertDay(
          tenantId,
          calendarId,
          row.date(),
          row.dayType(),
          row.workday(),
          null,
          row.holidayName(),
          "csv",
          row.remark(),
          defaultActor(actor));
    }

    audit.record(
        new AuditRecordCommand(
            tenantId,
            defaultActor(actor),
            "platform.calendar.import",
            "platform_calendar",
            calendarId,
            detail("calendarId", calendarId, "rows", String.valueOf(rows.size()))));

    return rows.size();
  }

  public WorkdayCheckResponse checkWorkday(String tenantId, String calendarId, LocalDate date) {
    requireText(calendarId, "calendarId");
    if (date == null) {
      throw new IllegalArgumentException("date is required");
    }

    CalendarDayRecord day =
        repository
            .findDay(tenantId, calendarId, date)
            .orElseGet(() -> derivedDay(tenantId, calendarId, date));

    return new WorkdayCheckResponse(date, day.workday(), day.dayType(), day.holidayName());
  }

  public WorkdayCountResponse countWorkdays(
      String tenantId, String calendarId, LocalDate start, LocalDate end) {
    requireText(calendarId, "calendarId");
    requireRange(start, end);

    int count =
        (int)
            listDays(tenantId, calendarId, start, end).stream()
                .filter(CalendarDayRecord::workday)
                .count();

    return new WorkdayCountResponse(start, end, count);
  }

  private void initializeYearDays(String tenantId, String calendarId, int year, String actor) {
    LocalDate start = LocalDate.of(year, 1, 1);
    LocalDate end = LocalDate.of(year, 12, 31);

    start
        .datesUntil(end.plusDays(1))
        .forEach(
            date -> {
              CalendarDayRecord derived = derivedDay(tenantId, calendarId, date);
              repository.upsertDay(
                  tenantId,
                  calendarId,
                  date,
                  derived.dayType(),
                  derived.workday(),
                  null,
                  null,
                  "generated",
                  null,
                  actor);
            });
  }

  private CalendarDayRecord derivedDay(String tenantId, String calendarId, LocalDate date) {
    int dayOfWeek = date.getDayOfWeek().getValue();
    boolean workday = dayOfWeek < 6;
    return new CalendarDayRecord(
        "derived-" + date,
        tenantId,
        calendarId,
        date,
        dayOfWeek,
        workday ? "WORKDAY" : "WEEKEND",
        workday,
        null,
        null,
        "derived",
        null,
        "system",
        null,
        null);
  }

  private void validateDayType(String dayType) {
    switch (dayType) {
      case "WORKDAY":
      case "WEEKEND":
      case "HOLIDAY":
      case "ADJUSTED_WORKDAY":
      case "COMPANY_HOLIDAY":
      case "COMPANY_WORKDAY":
        return;
      default:
        throw new IllegalArgumentException("unsupported dayType: " + dayType);
    }
  }

  private void requireRange(LocalDate start, LocalDate end) {
    if (start == null || end == null) {
      throw new IllegalArgumentException("start and end are required");
    }
    if (end.isBefore(start)) {
      throw new IllegalArgumentException("end must not be before start");
    }
  }

  private void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
  }

  private String defaultActor(String actor) {
    return actor == null || actor.isBlank() ? "system" : actor;
  }

  private String detail(String... keyValues) {
    StringBuilder sb = new StringBuilder("{");
    for (int i = 0; i + 1 < keyValues.length; i += 2) {
      if (i > 0) {
        sb.append(',');
      }
      String key = keyValues[i];
      String val = keyValues[i + 1];
      sb.append('"').append(escape(key)).append('"');
      sb.append(':');
      sb.append('"').append(escape(val == null ? "" : val)).append('"');
    }
    sb.append('}');
    return sb.toString();
  }

  private String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
