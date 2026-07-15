package io.aegisops.workrecord.application.port;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

public interface ReminderRepository {
  ReminderRuleView create(CreateRule command);

  List<ReminderRuleView> list(String tenantId);

  boolean setEnabled(String tenantId, String ruleId, boolean enabled);

  List<ReminderRule> findDueRules(Instant now, int limit);

  boolean hasRecord(
      String tenantId, String templateId, String userId, LocalDate date, ZoneId zoneId);

  List<String> usersByRole(String tenantId, String roleCode);

  record ReminderRule(
      String id,
      String tenantId,
      String templateId,
      String targetType,
      String targetJson,
      LocalTime cutoffTime,
      String timeZone) {}

  record CreateRule(
      String tenantId,
      String name,
      String templateId,
      String targetType,
      String targetJson,
      LocalTime cutoffTime,
      String timeZone,
      String actorId) {}

  record ReminderRuleView(
      String id,
      String name,
      String templateId,
      String targetType,
      String targetJson,
      LocalTime cutoffTime,
      String timeZone,
      boolean enabled) {}
}
