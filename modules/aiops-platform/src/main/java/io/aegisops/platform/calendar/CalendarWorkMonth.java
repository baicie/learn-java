package io.aegisops.platform.calendar;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record CalendarWorkMonth(
    CalendarRecord calendar,
    YearMonth month,
    LocalDate periodStart,
    LocalDate periodEnd,
    int workdayCount,
    LocalDate firstWorkday,
    LocalDate lastWorkday,
    List<LocalDate> workdays) {

  public CalendarWorkMonth {
    workdays = workdays == null ? List.of() : List.copyOf(workdays);
  }
}
