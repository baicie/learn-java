package io.aegisops.workrecord.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.security.PermissionCodes;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.ReminderRepository;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordReminderRuleService {
  private final ReminderRepository reminders;
  private final ObjectMapper objectMapper;

  public WorkRecordReminderRuleService(ReminderRepository reminders, ObjectMapper objectMapper) {
    this.reminders = reminders;
    this.objectMapper = objectMapper;
  }

  public List<ReminderRepository.ReminderRuleView> list(String tenantId, UserPrincipal principal) {
    requirePermission(tenantId, principal);
    return reminders.list(tenantId);
  }

  public ReminderRepository.ReminderRuleView create(
      String tenantId, CreateReminderRule command, UserPrincipal principal) {
    requirePermission(tenantId, principal);
    if (command == null
        || command.name() == null
        || command.name().isBlank()
        || command.name().length() > 160
        || command.templateId() == null
        || command.templateId().isBlank()
        || command.cutoffTime() == null) {
      throw new IllegalArgumentException("valid reminder rule fields are required");
    }
    ZoneId.of(command.timeZone());
    if (!"users".equals(command.targetType()) && !"role".equals(command.targetType())) {
      throw new IllegalArgumentException("targetType must be users or role");
    }
    validateTarget(command.targetType(), command.targetJson());
    return reminders.create(
        new ReminderRepository.CreateRule(
            tenantId,
            command.name().trim(),
            command.templateId(),
            command.targetType(),
            command.targetJson(),
            command.cutoffTime(),
            command.timeZone(),
            principal.id()));
  }

  public boolean setEnabled(String tenantId, String id, boolean enabled, UserPrincipal principal) {
    requirePermission(tenantId, principal);
    return reminders.setEnabled(tenantId, id, enabled);
  }

  private void validateTarget(String type, String json) {
    try {
      var node = objectMapper.readTree(json);
      if (node == null || !node.isObject()) {
        throw new IllegalArgumentException("targetJson must be an object");
      }
      if ("users".equals(type)
          && (!node.path("userIds").isArray()
              || node.path("userIds").isEmpty()
              || node.path("userIds").size() > 200)) {
        throw new IllegalArgumentException("userIds must contain 1 to 200 users");
      }
      if ("role".equals(type) && node.path("roleCode").asText().isBlank()) {
        throw new IllegalArgumentException("roleCode is required");
      }
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("targetJson is invalid", ex);
    }
  }

  private static void requirePermission(String tenantId, UserPrincipal principal) {
    if (principal == null
        || !tenantId.equals(principal.tenantId())
        || !principal.hasPermission(PermissionCodes.WORK_RECORD_REMINDER_MANAGE)) {
      throw new AccessDeniedException("not allowed to manage reminder rules");
    }
  }

  public record CreateReminderRule(
      String name,
      String templateId,
      String targetType,
      String targetJson,
      LocalTime cutoffTime,
      String timeZone) {}
}
