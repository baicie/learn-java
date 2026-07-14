package io.aegisops.workrecord.application.service;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.WorkRecordCalendarPort;
import io.aegisops.workrecord.application.port.WorkRecordRepository;
import io.aegisops.workrecord.application.port.WorkflowRepository;
import io.aegisops.workrecord.application.port.WorkflowRepository.ApprovalDefinition;
import io.aegisops.workrecord.application.port.WorkflowRepository.ApprovalTask;
import io.aegisops.workrecord.application.port.WorkflowRepository.ApprovalTaskView;
import io.aegisops.workrecord.application.port.WorkflowRepository.SlaInstanceView;
import io.aegisops.workrecord.application.port.WorkflowRepository.SlaPolicy;
import io.aegisops.workrecord.domain.model.RecordStatus;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkRecordLifecycleCoordinator {
  private final WorkflowRepository repository;
  private final WorkRecordCalendarPort calendar;
  private WorkRecordRepository records;
  private WorkRecordAuditService audit;
  private WorkRecordAuditSnapshots snapshots;
  private WorkRecordQueryService queries;

  public WorkRecordLifecycleCoordinator(
      WorkflowRepository repository, WorkRecordCalendarPort calendar) {
    this.repository = repository;
    this.calendar = calendar;
  }

  @Autowired
  void setAuditDependencies(
      WorkRecordRepository records,
      WorkRecordAuditService audit,
      WorkRecordAuditSnapshots snapshots,
      WorkRecordQueryService queries) {
    this.records = records;
    this.audit = audit;
    this.snapshots = snapshots;
    this.queries = queries;
  }

  public RecordStatus beforeTransition(
      String tenantId, WorkRecord record, RecordStatus target, UserPrincipal principal) {
    if (target != RecordStatus.DONE || record.status() == RecordStatus.DONE) return target;
    ApprovalDefinition definition =
        repository.findApprovalDefinition(tenantId, record.templateId()).orElse(null);
    if (definition == null) return target;
    OffsetDateTime dueAt =
        definition.timeoutMinutes() == null
            ? null
            : OffsetDateTime.now().plusMinutes(definition.timeoutMinutes());
    repository.startApproval(tenantId, record.id(), definition, principal.id(), dueAt);
    return RecordStatus.PENDING_APPROVAL;
  }

  public void afterMutation(String tenantId, WorkRecord before, WorkRecord after) {
    if (after == null || before != null && before.status() == after.status()) return;
    for (SlaPolicy policy : repository.findSlaPolicies(tenantId, after.templateId())) {
      if (after.status().value().equals(policy.startEvent())) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime dueAt =
            policy.calendarAware()
                ? OffsetDateTime.ofInstant(
                    calendar.addWorkingMinutes(tenantId, now.toInstant(), policy.targetMinutes()),
                    now.getOffset())
                : now.plusMinutes(policy.targetMinutes());
        repository.startSla(tenantId, after.id(), policy.id(), now, dueAt);
      }
      if (after.status().value().equals(policy.stopEvent())) {
        repository.stopSla(tenantId, after.id(), policy.id());
      }
    }
  }

  @Transactional
  public String act(
      String tenantId, String taskId, boolean approved, String comment, UserPrincipal user) {
    ApprovalTask task =
        repository
            .lockPendingTask(tenantId, taskId)
            .orElseThrow(() -> new IllegalStateException("approval task is already finished"));
    boolean assigned = isAssigned(task, user);
    if (!assigned) throw new AccessDeniedException("not assigned to this approval task");
    WorkRecord before =
        records == null ? null : records.find(tenantId, task.recordId()).orElse(null);
    repository.finishApproval(
        new WorkflowRepository.ApprovalAction(
            tenantId, taskId, task.instanceId(), task.recordId(), approved, user.id(), comment));
    if (before != null) {
      WorkRecord after = records.find(tenantId, task.recordId()).orElseThrow();
      afterMutation(tenantId, before, after);
      audit.recordChange(
          tenantId,
          after.id(),
          after.templateId(),
          "work_record",
          after.id(),
          WorkRecordAuditActions.RECORD_APPROVAL,
          user.id(),
          snapshots.record(before),
          snapshots.record(after),
          Map.of("approved", approved, "taskId", taskId));
    }
    return approved ? "approved" : "rejected";
  }

  public List<ApprovalTaskView> pendingTasks(String tenantId, UserPrincipal user) {
    if (user == null || !tenantId.equals(user.tenantId())) {
      throw new AccessDeniedException("tenant mismatch");
    }
    return repository.listPendingTasks(tenantId, user.id(), user.roles());
  }

  public List<SlaInstanceView> slaInstances(
      String tenantId, String recordId, UserPrincipal principal) {
    queries.get(tenantId, recordId, principal);
    return repository.listSlaInstances(tenantId, recordId);
  }

  @Transactional
  public int markBreached(OffsetDateTime now, int limit) {
    return repository.markBreached(now, limit);
  }

  private static boolean isAssigned(ApprovalTask task, UserPrincipal user) {
    return switch (task.assigneeType()) {
      case "user" -> task.assigneeValue().equals(user.id());
      case "role" -> user.roles().contains(task.assigneeValue());
      case "record_owner" -> user.id().equals(task.ownerId());
      default -> false;
    };
  }
}
