package io.aegisops.workrecord.application.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.port.NotificationRepository;
import io.aegisops.workrecord.application.port.ReminderRepository;
import io.aegisops.workrecord.application.port.WorkRecordCalendarPort;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MissingDailyReminderServiceTest {
  @Test
  void holidayDoesNotCreateNotification() {
    Fixture fixture = new Fixture(false);
    fixture.service.scanDueRules(Instant.parse("2026-07-20T10:00:00Z"));
    verify(fixture.notifications, never()).insertIfAbsent(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void adjustedWeekendWorkdayCreatesDeduplicatedNotification() {
    Fixture fixture = new Fixture(true);
    fixture.service.scanDueRules(Instant.parse("2026-07-19T10:00:00Z"));
    verify(fixture.notifications)
        .insertIfAbsent(
            org.mockito.ArgumentMatchers.argThat(
                value ->
                    value.userId().equals("user-1")
                        && value.dedupeKey().equals("daily-missing:template-1:2026-07-19:user-1")));
  }

  private static final class Fixture {
    final ReminderRepository reminders = mock(ReminderRepository.class);
    final WorkRecordCalendarPort calendar = mock(WorkRecordCalendarPort.class);
    final NotificationRepository notifications = mock(NotificationRepository.class);
    final MissingDailyReminderService service;

    Fixture(boolean workday) {
      var rule =
          new ReminderRepository.ReminderRule(
              "rule-1",
              "tenant-1",
              "template-1",
              "users",
              "{\"userIds\":[\"user-1\"]}",
              LocalTime.of(18, 0),
              "Asia/Shanghai");
      when(reminders.findDueRules(
              org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
          .thenReturn(List.of(rule));
      when(calendar.isWorkday("tenant-1", LocalDate.of(2026, 7, workday ? 19 : 20)))
          .thenReturn(workday);
      when(reminders.hasRecord(
              org.mockito.ArgumentMatchers.any(),
              org.mockito.ArgumentMatchers.any(),
              org.mockito.ArgumentMatchers.any(),
              org.mockito.ArgumentMatchers.any(),
              org.mockito.ArgumentMatchers.any()))
          .thenReturn(false);
      service =
          new MissingDailyReminderService(reminders, calendar, notifications, new ObjectMapper());
    }
  }
}
