package io.aegisops.platform.calendar;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class CalendarCsvImporter {

  public List<CalendarCsvRow> parse(String csv) {
    if (csv == null || csv.isBlank()) {
      throw new IllegalArgumentException("csv is required");
    }

    String[] lines = csv.replace("\r\n", "\n").replace('\r', '\n').split("\n");
    List<CalendarCsvRow> rows = new ArrayList<>();

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i].trim();
      if (line.isEmpty()) {
        continue;
      }
      if (i == 0 && line.toLowerCase().startsWith("date,")) {
        continue;
      }

      String[] parts = splitCsvLine(line);
      if (parts.length < 3) {
        throw new IllegalArgumentException("invalid csv line " + (i + 1));
      }

      LocalDate date = parseDate(parts[0].trim(), i + 1);
      String dayType = parts[1].trim();
      validateDayType(dayType, i + 1);
      boolean workday = parseBoolean(parts[2].trim(), i + 1);
      String holidayName = parts.length > 3 ? blankToNull(parts[3].trim()) : null;
      String remark = parts.length > 4 ? blankToNull(parts[4].trim()) : null;

      rows.add(new CalendarCsvRow(date, dayType, workday, holidayName, remark));
    }

    if (rows.isEmpty()) {
      throw new IllegalArgumentException("csv has no data rows");
    }

    return rows;
  }

  private LocalDate parseDate(String value, int line) {
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException ex) {
      throw new IllegalArgumentException("invalid date at csv line " + line + ": " + value, ex);
    }
  }

  private boolean parseBoolean(String value, int line) {
    if ("true".equalsIgnoreCase(value)) {
      return true;
    }
    if ("false".equalsIgnoreCase(value)) {
      return false;
    }
    throw new IllegalArgumentException("invalid isWorkday at csv line " + line + ": " + value);
  }

  private void validateDayType(String dayType, int line) {
    switch (dayType) {
      case "WORKDAY":
      case "WEEKEND":
      case "HOLIDAY":
      case "ADJUSTED_WORKDAY":
      case "COMPANY_HOLIDAY":
      case "COMPANY_WORKDAY":
        return;
      default:
        throw new IllegalArgumentException("invalid dayType at csv line " + line + ": " + dayType);
    }
  }

  private String[] splitCsvLine(String line) {
    return line.split(",", -1);
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  public record CalendarCsvRow(
      LocalDate date, String dayType, boolean workday, String holidayName, String remark) {}
}
