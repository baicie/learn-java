package io.aegisops.platform.calendar;

import io.aegisops.platform.audit.PlatformAuditService;
import java.io.IOException;
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
import org.springframework.web.multipart.MultipartFile;

@Service
public class CalendarService {
  private static final int MIN_YEAR = 2000;
  private static final int MAX_YEAR = 2050;
  private static final long MAX_IMPORT_FILE_BYTES = 5L * 1024 * 1024;
  private static final int MAX_IMPORT_ROWS = 1000;
  private static final int CHANGED_DATE_LIMIT = 200;

  private final CalendarRepository repository;
  private final PlatformAuditService audit;
  private final CalendarXlsxImporter xlsxImporter = new CalendarXlsxImporter();

  public CalendarService(CalendarRepository repository, PlatformAuditService audit) {
    this.repository = repository;
    this.audit = audit;
  }

  public List<CalendarRecord> listCalendars(String tenantId) {
    return repository.listCalendars(tenantId).stream()
        .filter(calendar -> isSupportedYear(calendar.year()))
        .toList();
  }

  public List<CalendarDayRecord> listDays(
      String tenantId, String calendarId, LocalDate start, LocalDate end) {
    requireText(calendarId, "calendarId");
    requireRange(start, end);
    CalendarRecord calendar = requireSupportedCalendar(tenantId, calendarId);
    requireCalendarRange(calendar, start, end);

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

    CalendarRecord calendar = requireSupportedCalendar(tenantId, calendarId);
    requireCalendarDate(calendar, date);

    CalendarDayRecord before =
        repository
            .findDay(tenantId, calendarId, date)
            .orElseGet(() -> derivedDay(tenantId, calendarId, date));

    CalendarDayRecord after =
        repository.upsertDay(
            tenantId,
            calendarId,
            new CalendarDayMutation(
                date,
                request.dayType(),
                request.workday(),
                request.holidayCode(),
                request.holidayName(),
                request.sourceType(),
                request.remark()),
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
  public int importXlsx(String tenantId, String calendarId, MultipartFile file, String actor) {
    ParsedImport parsedImport = parseImport(tenantId, calendarId, file);
    List<CalendarXlsxImporter.CalendarImportRow> rows = parsedImport.rows();

    String effectiveActor = defaultActor(actor);

    int createdCount = 0;
    int overwrittenCount = 0;
    int unchangedCount = 0;
    List<String> changedDates = new ArrayList<>();

    for (CalendarXlsxImporter.CalendarImportRow row : rows) {
      CalendarDayRecord before = repository.findDay(tenantId, calendarId, row.date()).orElse(null);

      CalendarDayRecord after =
          repository.upsertDay(
              tenantId,
              calendarId,
              new CalendarDayMutation(
                  row.date(), "HOLIDAY", false, null, row.holidayName(), "xlsx", row.remark()),
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

    auditImport(
        tenantId,
        calendarId,
        effectiveActor,
        sha256(parsedImport.content()),
        rows.size(),
        new ImportCounts(createdCount, overwrittenCount, unchangedCount, changedDates));

    return rows.size();
  }

  private ParsedImport parseImport(String tenantId, String calendarId, MultipartFile file) {
    requireText(calendarId, "calendarId");
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("xlsx file is required");
    }
    if (file.getSize() > MAX_IMPORT_FILE_BYTES) {
      throw new IllegalArgumentException("xlsx file must not exceed 5 MB");
    }
    String originalFilename = file.getOriginalFilename();
    if (originalFilename == null
        || !originalFilename.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) {
      throw new IllegalArgumentException("calendar import must be an xlsx file");
    }
    CalendarRecord calendar = requireSupportedCalendar(tenantId, calendarId);
    byte[] content = fileBytes(file);
    List<CalendarXlsxImporter.CalendarImportRow> rows = xlsxImporter.parse(content);
    if (rows.size() > MAX_IMPORT_ROWS) {
      throw new IllegalArgumentException("holiday import exceeds " + MAX_IMPORT_ROWS + " rows");
    }
    if (rows.stream().anyMatch(row -> row.date().getYear() != calendar.year())) {
      throw new IllegalArgumentException("holiday date must belong to the selected calendar year");
    }
    return new ParsedImport(rows, content);
  }

  private void auditImport(
      String tenantId,
      String calendarId,
      String actor,
      String contentSha256,
      int rowCount,
      ImportCounts counts) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("rowCount", rowCount);
    summary.put("createdCount", counts.created());
    summary.put("overwrittenCount", counts.overwritten());
    summary.put("unchangedCount", counts.unchanged());
    summary.put("changedDates", counts.changedDates());
    summary.put(
        "changedDatesTruncated",
        (counts.created() + counts.overwritten()) > counts.changedDates().size());
    summary.put("xlsxSha256", contentSha256);
    audit.recordChange(
        tenantId,
        actor,
        "platform.calendar.import",
        "platform_calendar",
        calendarId,
        Map.of(),
        summary,
        Map.of("calendarId", calendarId, "source", "xlsx"));
  }

  private record ImportCounts(
      int created, int overwritten, int unchanged, List<String> changedDates) {}

  private record ParsedImport(List<CalendarXlsxImporter.CalendarImportRow> rows, byte[] content) {}

  public WorkdayCheckResponse checkWorkday(String tenantId, String calendarId, LocalDate date) {
    requireText(calendarId, "calendarId");
    if (date == null) {
      throw new IllegalArgumentException("date is required");
    }
    CalendarRecord calendar = requireSupportedCalendar(tenantId, calendarId);
    requireCalendarDate(calendar, date);

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
                .filter(day -> day.workday())
                .count();

    return new WorkdayCountResponse(start, end, count);
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

  private byte[] fileBytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException ex) {
      throw new IllegalArgumentException("unable to read xlsx file", ex);
    }
  }

  private String sha256(byte[] value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
  }

  public byte[] createHolidayImportTemplate(int year) {
    validateSupportedYear(year);
    return xlsxImporter.createTemplate(year);
  }

  private CalendarRecord requireSupportedCalendar(String tenantId, String calendarId) {
    CalendarRecord calendar =
        repository
            .findCalendar(tenantId, calendarId)
            .orElseThrow(() -> new IllegalArgumentException("calendar not found"));
    validateSupportedYear(calendar.year());
    return calendar;
  }

  private void requireCalendarDate(CalendarRecord calendar, LocalDate date) {
    if (date.getYear() != calendar.year()) {
      throw new IllegalArgumentException("date must belong to the selected calendar year");
    }
  }

  private void requireCalendarRange(CalendarRecord calendar, LocalDate start, LocalDate end) {
    if (start.getYear() != calendar.year() || end.getYear() != calendar.year()) {
      throw new IllegalArgumentException("date range must belong to the selected calendar year");
    }
  }

  private void validateSupportedYear(int year) {
    if (!isSupportedYear(year)) {
      throw new IllegalArgumentException("year must be between " + MIN_YEAR + " and " + MAX_YEAR);
    }
  }

  private boolean isSupportedYear(int year) {
    return year >= MIN_YEAR && year <= MAX_YEAR;
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
}
