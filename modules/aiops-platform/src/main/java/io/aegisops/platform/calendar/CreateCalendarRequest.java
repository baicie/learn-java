package io.aegisops.platform.calendar;

public record CreateCalendarRequest(
    String calendarCode,
    String calendarName,
    String regionCode,
    String timezone,
    Integer year,
    Boolean enabled,
    String sourceType,
    String description) {}
