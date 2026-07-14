package io.aegisops.workrecord.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.id.Ids;
import io.aegisops.workrecord.application.port.NotificationRepository;
import io.aegisops.workrecord.application.port.ReminderRepository;
import io.aegisops.workrecord.application.port.WorkRecordCalendarPort;
import io.aegisops.workrecord.domain.model.WorkRecordNotification;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class MissingDailyReminderService {
  private final ReminderRepository reminders;
  private final WorkRecordCalendarPort calendar;
  private final NotificationRepository notifications;
  private final ObjectMapper objectMapper;

  public MissingDailyReminderService(
      ReminderRepository reminders,
      WorkRecordCalendarPort calendar,
      NotificationRepository notifications,
      ObjectMapper objectMapper) {
    this.reminders = reminders;
    this.calendar = calendar;
    this.notifications = notifications;
    this.objectMapper = objectMapper;
  }

  public void scanDueRules(Instant now) {
    for (var rule : reminders.findDueRules(now, 200)) {
      ZoneId zone = ZoneId.of(rule.timeZone());
      LocalDate date = now.atZone(zone).toLocalDate();
      if (!calendar.isWorkday(rule.tenantId(), date)) {
        continue;
      }
      for (String userId : targetUsers(rule)) {
        if (!reminders.hasRecord(rule.tenantId(), rule.templateId(), userId, date, zone)) {
          notifications.insertIfAbsent(
              new WorkRecordNotification(
                  Ids.newId(),
                  rule.tenantId(),
                  userId,
                  "daily_record_missing",
                  "日报尚未填写",
                  date + " 为工作日，请及时填写日报",
                  "work_record_template",
                  rule.templateId(),
                  "daily-missing:" + rule.templateId() + ":" + date + ":" + userId,
                  null,
                  null));
        }
      }
    }
  }

  private Set<String> targetUsers(ReminderRepository.ReminderRule rule) {
    try {
      var node = objectMapper.readTree(rule.targetJson());
      if ("users".equals(rule.targetType())) {
        LinkedHashSet<String> users = new LinkedHashSet<>();
        node.path("userIds")
            .forEach(
                value -> {
                  if (!value.asText().isBlank()) {
                    users.add(value.asText());
                  }
                });
        return users;
      }
      return new LinkedHashSet<>(
          reminders.usersByRole(rule.tenantId(), node.path("roleCode").asText()));
    } catch (Exception ex) {
      throw new IllegalStateException("invalid reminder target", ex);
    }
  }
}
