package io.aegisops.workrecord.application.service;

import io.aegisops.workrecord.application.command.RecordWorkdaySummary;
import io.aegisops.workrecord.application.port.WorkRecordCalendarPort;
import io.aegisops.workrecord.application.port.WorkRecordWorkMonth;
import java.time.Clock;
import java.time.YearMonth;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordWorkdaySummaryService {
  private final WorkRecordCalendarPort calendarPort;
  private final Clock clock;

  public WorkRecordWorkdaySummaryService(
      WorkRecordCalendarPort calendarPort, @Qualifier("workRecordClock") Clock clock) {
    this.calendarPort = calendarPort;
    this.clock = clock;
  }

  public RecordWorkdaySummary currentMonth(String tenantId) {
    return map(calendarPort.currentWorkMonth(tenantId, clock.instant()));
  }

  public RecordWorkdaySummary month(String tenantId, YearMonth month) {
    if (month == null) {
      return currentMonth(tenantId);
    }

    return map(calendarPort.workMonth(tenantId, month));
  }

  private RecordWorkdaySummary map(WorkRecordWorkMonth month) {
    return new RecordWorkdaySummary(
        month.calendarId(),
        month.calendarName(),
        month.timeZone(),
        month.month().toString(),
        month.periodStart(),
        month.periodEnd(),
        month.workdayCount(),
        month.firstWorkday(),
        month.lastWorkday());
  }
}
