package io.aegisops.workrecord.application.port;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface WorkflowRepository {
  Optional<ApprovalDefinition> findApprovalDefinition(String tenantId, String templateId);

  void startApproval(
      String tenantId,
      String recordId,
      ApprovalDefinition definition,
      String actorId,
      OffsetDateTime dueAt);

  List<SlaPolicy> findSlaPolicies(String tenantId, String templateId);

  void startSla(
      String tenantId,
      String recordId,
      String policyId,
      OffsetDateTime startedAt,
      OffsetDateTime dueAt);

  void stopSla(String tenantId, String recordId, String policyId);

  Optional<ApprovalTask> lockPendingTask(String tenantId, String taskId);

  List<ApprovalTaskView> listPendingTasks(String tenantId, String userId, Set<String> roles);

  List<SlaInstanceView> listSlaInstances(String tenantId, String recordId);

  void finishApproval(ApprovalAction action);

  record ApprovalAction(
      String tenantId,
      String taskId,
      String instanceId,
      String recordId,
      boolean approved,
      String actorId,
      String comment) {}

  int markBreached(OffsetDateTime now, int limit);

  int nextApprovalVersion(String tenantId, String templateId);

  void disableApprovals(String tenantId, String templateId);

  void createApprovalDefinition(CreateApprovalDefinition command);

  record CreateApprovalDefinition(
      String id, String tenantId, String templateId, String name, int version, String actorId) {}

  void createApprovalStep(
      String id,
      String definitionId,
      String approverType,
      String approverValue,
      Integer timeoutMinutes);

  void createSlaPolicy(CreateSlaPolicy command);

  record CreateSlaPolicy(
      String id,
      String tenantId,
      String templateId,
      String name,
      String startEvent,
      String stopEvent,
      int targetMinutes,
      boolean calendarAware,
      String severity,
      String actorId) {}

  record ApprovalDefinition(
      String id, String approverType, String approverValue, Integer timeoutMinutes) {}

  record SlaPolicy(
      String id, String startEvent, String stopEvent, int targetMinutes, boolean calendarAware) {}

  record ApprovalTask(
      String instanceId,
      String recordId,
      String ownerId,
      String assigneeType,
      String assigneeValue) {}

  record ApprovalTaskView(
      String id,
      String instanceId,
      String recordId,
      String recordTitle,
      String assigneeType,
      String assigneeValue,
      OffsetDateTime dueAt,
      OffsetDateTime createdAt) {}

  record SlaInstanceView(
      String id,
      String recordId,
      String policyName,
      String status,
      OffsetDateTime startedAt,
      OffsetDateTime dueAt,
      OffsetDateTime stoppedAt,
      OffsetDateTime breachedAt,
      String severity) {}
}
