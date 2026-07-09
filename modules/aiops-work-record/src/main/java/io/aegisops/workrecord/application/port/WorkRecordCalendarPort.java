package io.aegisops.workrecord.application.port;

import java.time.LocalDate;

public interface WorkRecordCalendarPort {
  boolean isWorkday(String tenantId, String calendarId, LocalDate date);
}
