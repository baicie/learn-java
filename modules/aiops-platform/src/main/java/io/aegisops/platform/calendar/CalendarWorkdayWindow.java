package io.aegisops.platform.calendar;

import java.time.LocalDate;
import java.util.List;

public record CalendarWorkdayWindow(
    CalendarRecord calendar,
    LocalDate anchorDate,
    LocalDate periodStart,
    LocalDate periodEnd,
    List<LocalDate> workdays) {

  public CalendarWorkdayWindow {
    workdays = workdays == null ? List.of() : List.copyOf(workdays);
  }
}
