package io.aegisops.workrecord.application.port;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record WorkRecordWorkMonth(
    String calendarId,
    String calendarName,
    String timeZone,
    YearMonth month,
    LocalDate periodStart,
    LocalDate periodEnd,
    int workdayCount,
    LocalDate firstWorkday,
    LocalDate lastWorkday,
    List<LocalDate> workdays) {

  public WorkRecordWorkMonth {
    workdays = workdays == null ? List.of() : List.copyOf(workdays);
  }
}
