package io.aegisops.platform.calendar;

import java.time.LocalDate;

public record CalendarDayMutation(
    LocalDate date,
    String dayType,
    boolean workday,
    String holidayCode,
    String holidayName,
    String sourceType,
    String remark) {}
