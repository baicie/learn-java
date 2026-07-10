package io.aegisops.platform.calendar;

import io.aegisops.platform.audit.PlatformAuditService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarService {
  private static final int MAX_IMPORT_ROWS = 1000;
  private static final int CHANGED_DATE_LIMIT = 200;

  private final CalendarRepository repository;
  private final PlatformAuditService audit;
  private final CalendarCsvImporter csvImporter = new CalendarCsvImporter();

  public CalendarService(CalendarRepository repository, PlatformAuditService audit) {
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

    String timezone = normalizeTimezone(request.timezone());

    CreateCalendarRequest normalized =
        new CreateCalendarRequest(
            request.calendarCode(),
            request.calendarName(),
            request.regionCode(),
            timezone,
            request.year(),
            request.enabled(),
            request.sourceType(),
            request.description());

    String effectiveActor = defaultActor(actor);

    CalendarRecord record = repository.createCalendar(tenantId, normalized, effectiveActor);
    initializeYearDays(tenantId, record.id(), normalized.year(), effectiveActor);

    audit.recordChange(
        tenantId,
        effectiveActor,
        "platform.calendar.create",
        "platform_calendar",
        record.id(),
        Map.of(),
        record,
        Map.of("calendarCode", record.calendarCode(), "year", String.valueOf(record.year())));
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

    CalendarDayRecord before =
        repository
            .findDay(tenantId, calendarId, date)
            .orElseGet(() -> derivedDay(tenantId, calendarId, date));

    CalendarDayRecord after =
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

    audit.recordChange(
        tenantId,
        defaultActor(actor),
        "platform.calendar.day.override",
        "platform_calendar_day",
        after.id(),
        semanticDay(before),
        semanticDay(after),
        Map.of("calendarId", calendarId, "date", date.toString(), "source", "manual"));

    return after;
  }

  @Transactional
  public int importCsv(
      String tenantId, String calendarId, ImportCalendarCsvRequest request, String actor) {
    requireText(calendarId, "calendarId");

    if (request == null) {
      throw new IllegalArgumentException("calendar import request is required");
    }

    repository
        .findCalendar(tenantId, calendarId)
        .orElseThrow(() -> new IllegalArgumentException("calendar not found"));

    List<CalendarCsvImporter.CalendarCsvRow> rows = csvImporter.parse(request.csv());

    if (rows.size() > MAX_IMPORT_ROWS) {
      throw new IllegalArgumentException("calendar import exceeds " + MAX_IMPORT_ROWS + " rows");
    }

    String effectiveActor = defaultActor(actor);

    int createdCount = 0;
    int overwrittenCount = 0;
    int unchangedCount = 0;
    List<String> changedDates = new ArrayList<>();

    for (CalendarCsvImporter.CalendarCsvRow row : rows) {
      CalendarDayRecord before = repository.findDay(tenantId, calendarId, row.date()).orElse(null);

      CalendarDayRecord after =
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
              effectiveActor);

      Map<String, Object> afterSnapshot = semanticDay(after);

      if (before == null) {
        createdCount++;
        appendChangedDate(changedDates, row.date());

        audit.recordChange(
            tenantId,
            effectiveActor,
            "platform.calendar.day.import_create",
            "platform_calendar_day",
            after.id(),
            Map.of(),
            afterSnapshot,
            Map.of("calendarId", calendarId, "date", row.date().toString()));
        continue;
      }

      Map<String, Object> beforeSnapshot = semanticDay(before);

      if (beforeSnapshot.equals(afterSnapshot)) {
        unchangedCount++;
        continue;
      }

      overwrittenCount++;
      appendChangedDate(changedDates, row.date());

      audit.recordChange(
          tenantId,
          effectiveActor,
          "platform.calendar.day.import_overwrite",
          "platform_calendar_day",
          after.id(),
          beforeSnapshot,
          afterSnapshot,
          Map.of("calendarId", calendarId, "date", row.date().toString()));
    }

    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("rowCount", rows.size());
    summary.put("createdCount", createdCount);
    summary.put("overwrittenCount", overwrittenCount);
    summary.put("unchangedCount", unchangedCount);
    summary.put("changedDates", changedDates);
    summary.put("changedDatesTruncated", (createdCount + overwrittenCount) > changedDates.size());
    summary.put("csvSha256", sha256(request.csv()));

    audit.recordChange(
        tenantId,
        effectiveActor,
        "platform.calendar.import",
        "platform_calendar",
        calendarId,
        Map.of(),
        summary,
        Map.of("calendarId", calendarId, "source", "csv"));

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

  private Map<String, Object> semanticDay(CalendarDayRecord day) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("calendarId", day.calendarId());
    result.put("calendarDate", day.calendarDate());
    result.put("dayOfWeek", day.dayOfWeek());
    result.put("dayType", day.dayType());
    result.put("workday", day.workday());
    result.put("holidayCode", day.holidayCode());
    result.put("holidayName", day.holidayName());
    result.put("sourceType", day.sourceType());
    result.put("remark", day.remark());
    return result;
  }

  private void appendChangedDate(List<String> target, LocalDate date) {
    if (target.size() < CHANGED_DATE_LIMIT) {
      target.add(date.toString());
    }
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
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

  private String normalizeTimezone(String value) {
    String timezone = value == null || value.isBlank() ? "Asia/Shanghai" : value.trim();

    try {
      java.time.ZoneId.of(timezone);
      return timezone;
    } catch (java.time.DateTimeException ex) {
      throw new IllegalArgumentException("invalid timezone: " + timezone, ex);
    }
  }

  private String defaultActor(String actor) {
    return actor == null || actor.isBlank() ? "system" : actor;
  }
}
