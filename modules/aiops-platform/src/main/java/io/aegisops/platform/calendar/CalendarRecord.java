package io.aegisops.platform.calendar;

import java.time.OffsetDateTime;

public record CalendarRecord(
    String id,
    String tenantId,
    String calendarCode,
    String calendarName,
    String regionCode,
    String timezone,
    int year,
    boolean enabled,
    String sourceType,
    String description,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
