package io.aegisops.platform.calendar;

import io.aegisops.audit.AuditRecordCommand;
import io.aegisops.audit.AuditService;
import java.time.LocalDate;
import java.util.List;
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
    requireText(request.calendarCode(), "calendarCode");
    requireText(request.calendarName(), "calendarName");
    if (request.year() == null || request.year() < 2000 || request.year() > 2100) {
      throw new IllegalArgumentException("year must be between 2000 and 2100");
    }

    CalendarRecord record = repository.createCalendar(tenantId, request, defaultActor(actor));
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
    return repository.listDays(tenantId, calendarId, start, end);
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
    requireText(request.dayType(), "dayType");
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
            .orElseGet(
                () ->
                    new CalendarDayRecord(
                        "",
                        tenantId,
                        calendarId,
                        date,
                        date.getDayOfWeek().getValue(),
                        date.getDayOfWeek().getValue() >= 6 ? "WEEKEND" : "WORKDAY",
                        date.getDayOfWeek().getValue() < 6,
                        null,
                        null,
                        "derived",
                        null,
                        "system",
                        null,
                        null));

    return new WorkdayCheckResponse(
        date, day.workday(), day.dayType(), day.holidayName());
  }

  public WorkdayCountResponse countWorkdays(
      String tenantId, String calendarId, LocalDate start, LocalDate end) {
    requireText(calendarId, "calendarId");
    requireRange(start, end);
    int count = repository.countWorkdays(tenantId, calendarId, start, end);
    return new WorkdayCountResponse(start, end, count);
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
