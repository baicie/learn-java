package io.aegisops.platform.calendar;

import java.time.LocalDate;

public final class WorkCalendarConfigurationException extends RuntimeException {

  private final String errorCode;

  public WorkCalendarConfigurationException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public String errorCode() {
    return errorCode;
  }

  public static WorkCalendarConfigurationException defaultCalendarMissing(int year) {
    return new WorkCalendarConfigurationException(
        "WORK_CALENDAR_NOT_CONFIGURED", "default work calendar is not configured for year " + year);
  }

  public static WorkCalendarConfigurationException incomplete(
      LocalDate start, LocalDate end, int expected, int actual) {
    return new WorkCalendarConfigurationException(
        "WORK_CALENDAR_INCOMPLETE",
        "work calendar data is incomplete for "
            + start
            + " to "
            + end
            + ": expected "
            + expected
            + " days but found "
            + actual);
  }

  public static WorkCalendarConfigurationException inconsistentWorkdays(
      LocalDate start, LocalDate end) {
    return new WorkCalendarConfigurationException(
        "WORK_CALENDAR_INCONSISTENT",
        "work calendar workday data is inconsistent for " + start + " to " + end);
  }

  public static WorkCalendarConfigurationException invalidTimezone(String timezone) {
    return new WorkCalendarConfigurationException(
        "WORK_CALENDAR_INVALID_TIMEZONE", "invalid work calendar timezone: " + timezone);
  }
}
