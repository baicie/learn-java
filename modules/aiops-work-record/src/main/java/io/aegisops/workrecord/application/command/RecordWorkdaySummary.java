package io.aegisops.workrecord.application.command;

import java.time.LocalDate;

public record RecordWorkdaySummary(
    String calendarId,
    String calendarName,
    String timeZone,
    String month,
    LocalDate periodStart,
    LocalDate periodEnd,
    int workdayCount,
    LocalDate firstWorkday,
    LocalDate lastWorkday) {}
