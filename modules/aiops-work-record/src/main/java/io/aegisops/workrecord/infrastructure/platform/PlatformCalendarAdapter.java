package io.aegisops.workrecord.infrastructure.platform;

import io.aegisops.platform.calendar.CalendarService;
import io.aegisops.workrecord.application.port.WorkRecordCalendarPort;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class PlatformCalendarAdapter implements WorkRecordCalendarPort {
  private final CalendarService calendarService;

  public PlatformCalendarAdapter(CalendarService calendarService) {
    this.calendarService = calendarService;
  }

  @Override
  public boolean isWorkday(String tenantId, String calendarId, LocalDate date) {
    return calendarService.checkWorkday(tenantId, calendarId, date).workday();
  }
}
