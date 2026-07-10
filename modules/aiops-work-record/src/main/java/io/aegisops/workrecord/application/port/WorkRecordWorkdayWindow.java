package io.aegisops.workrecord.application.port;

import java.time.LocalDate;
import java.util.List;

public record WorkRecordWorkdayWindow(
    String calendarId,
    String calendarName,
    String timeZone,
    LocalDate anchorDate,
    LocalDate periodStart,
    LocalDate periodEnd,
    List<LocalDate> workdays) {

  public WorkRecordWorkdayWindow {
    workdays = workdays == null ? List.of() : List.copyOf(workdays);
  }
}
