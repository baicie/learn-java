package io.aegisops.platform.calendar;

public record UpdateCalendarDayRequest(
    String dayType,
    Boolean workday,
    String holidayCode,
    String holidayName,
    String sourceType,
    String remark) {}
