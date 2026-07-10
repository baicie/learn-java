package io.aegisops.workrecord.infrastructure.platform;

import io.aegisops.platform.calendar.CalendarWorkMonth;
import io.aegisops.platform.calendar.CalendarWorkdayWindow;
import io.aegisops.platform.calendar.DefaultCalendarService;
import io.aegisops.workrecord.application.port.WorkRecordCalendarPort;
import io.aegisops.workrecord.application.port.WorkRecordWorkMonth;
import io.aegisops.workrecord.application.port.WorkRecordWorkdayWindow;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.stereotype.Component;

@Component
public class PlatformCalendarAdapter implements WorkRecordCalendarPort {
  private final DefaultCalendarService service;

  public PlatformCalendarAdapter(DefaultCalendarService service) {
    this.service = service;
  }

  @Override
  public WorkRecordWorkdayWindow recentWorkdays(String tenantId, Instant now, int count) {
    CalendarWorkdayWindow value = service.recentWorkdays(tenantId, now, count);

    return new WorkRecordWorkdayWindow(
        value.calendar().id(),
        value.calendar().calendarName(),
        value.calendar().timezone(),
        value.anchorDate(),
        value.periodStart(),
        value.periodEnd(),
        value.workdays());
  }

  @Override
  public WorkRecordWorkMonth currentWorkMonth(String tenantId, Instant now) {
    return map(service.currentWorkMonth(tenantId, now));
  }

  @Override
  public WorkRecordWorkMonth workMonth(String tenantId, YearMonth month) {
    return map(service.workMonth(tenantId, month));
  }

  @Override
  public boolean isWorkday(String tenantId, LocalDate date) {
    return service.isWorkday(tenantId, date);
  }

  private WorkRecordWorkMonth map(CalendarWorkMonth value) {
    return new WorkRecordWorkMonth(
        value.calendar().id(),
        value.calendar().calendarName(),
        value.calendar().timezone(),
        value.month(),
        value.periodStart(),
        value.periodEnd(),
        value.workdayCount(),
        value.firstWorkday(),
        value.lastWorkday(),
        value.workdays());
  }
}
