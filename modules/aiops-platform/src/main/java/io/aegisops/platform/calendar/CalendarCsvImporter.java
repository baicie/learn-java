package io.aegisops.platform.calendar;

import java.time.LocalDate;
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

      LocalDate date = LocalDate.parse(parts[0].trim());
      String dayType = parts[1].trim();
      boolean workday = Boolean.parseBoolean(parts[2].trim());
      String holidayName = parts.length > 3 ? blankToNull(parts[3].trim()) : null;
      String remark = parts.length > 4 ? blankToNull(parts[4].trim()) : null;

      rows.add(new CalendarCsvRow(date, dayType, workday, holidayName, remark));
    }

    return rows;
  }

  private String[] splitCsvLine(String line) {
    return line.split(",", -1);
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  public record CalendarCsvRow(
      LocalDate date, String dayType, boolean workday, String holidayName, String remark) {
  }
}
