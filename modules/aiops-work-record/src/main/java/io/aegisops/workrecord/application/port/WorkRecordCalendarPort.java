package io.aegisops.workrecord.application.port;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

public interface WorkRecordCalendarPort {
  WorkRecordWorkdayWindow recentWorkdays(String tenantId, Instant now, int count);

  WorkRecordWorkMonth currentWorkMonth(String tenantId, Instant now);

  WorkRecordWorkMonth workMonth(String tenantId, YearMonth month);

  boolean isWorkday(String tenantId, LocalDate date);

  int countWorkdays(String tenantId, Instant fromInclusive, Instant toExclusive);

  Instant addWorkingMinutes(String tenantId, Instant start, int minutes);
}
