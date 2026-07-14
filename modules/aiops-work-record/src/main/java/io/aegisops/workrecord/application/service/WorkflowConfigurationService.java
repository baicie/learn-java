package io.aegisops.workrecord.application.service;

import io.aegisops.common.id.Ids;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.WorkflowRepository;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowConfigurationService {
  private static final Set<String> APPROVER_TYPES = Set.of("user", "role", "record_owner");
  private static final Set<String> SEVERITIES = Set.of("info", "warning", "critical");
  private final WorkflowRepository repository;

  public WorkflowConfigurationService(WorkflowRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public String createApproval(String tenantId, ApprovalConfiguration command, UserPrincipal user) {
    requireText(command.name(), "name");
    if (!APPROVER_TYPES.contains(command.approverType())) {
      throw new IllegalArgumentException("unsupported approver type");
    }
    if (!"record_owner".equals(command.approverType())) {
      requireText(command.approverValue(), "approverValue");
    }
    if (command.timeoutMinutes() != null && command.timeoutMinutes() <= 0) {
      throw new IllegalArgumentException("timeoutMinutes must be positive");
    }
    String id = Ids.newId();
    int version = repository.nextApprovalVersion(tenantId, command.templateId());
    repository.disableApprovals(tenantId, command.templateId());
    repository.createApprovalDefinition(
        new WorkflowRepository.CreateApprovalDefinition(
            id, tenantId, command.templateId(), command.name(), version, user.id()));
    repository.createApprovalStep(
        Ids.newId(),
        id,
        command.approverType(),
        command.approverValue() == null ? "owner" : command.approverValue(),
        command.timeoutMinutes());
    return id;
  }

  public String createSla(String tenantId, SlaConfiguration command, UserPrincipal user) {
    requireText(command.name(), "name");
    requireText(command.startEvent(), "startEvent");
    requireText(command.stopEvent(), "stopEvent");
    if (command.startEvent().equals(command.stopEvent())) {
      throw new IllegalArgumentException("SLA events must differ");
    }
    if (command.targetMinutes() <= 0) {
      throw new IllegalArgumentException("targetMinutes must be positive");
    }
    if (!SEVERITIES.contains(command.severity())) {
      throw new IllegalArgumentException("unsupported severity");
    }
    String id = Ids.newId();
    repository.createSlaPolicy(
        new WorkflowRepository.CreateSlaPolicy(
            id,
            tenantId,
            command.templateId(),
            command.name(),
            command.startEvent(),
            command.stopEvent(),
            command.targetMinutes(),
            command.calendarAware(),
            command.severity(),
            user.id()));
    return id;
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
  }

  public record ApprovalConfiguration(
      String templateId,
      String name,
      String approverType,
      String approverValue,
      Integer timeoutMinutes) {}

  public record SlaConfiguration(
      String templateId,
      String name,
      String startEvent,
      String stopEvent,
      int targetMinutes,
      boolean calendarAware,
      String severity) {}
}
