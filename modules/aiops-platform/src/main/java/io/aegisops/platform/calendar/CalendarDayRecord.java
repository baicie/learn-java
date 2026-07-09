package io.aegisops.platform.calendar;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record CalendarDayRecord(
    String id,
    String tenantId,
    String calendarId,
    LocalDate calendarDate,
    int dayOfWeek,
    String dayType,
    boolean workday,
    String holidayCode,
    String holidayName,
    String sourceType,
    String remark,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
