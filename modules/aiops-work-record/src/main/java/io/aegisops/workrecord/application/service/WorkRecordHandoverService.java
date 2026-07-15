package io.aegisops.workrecord.application.service;

import io.aegisops.common.exception.ConflictException;
import io.aegisops.common.exception.ResourceNotFoundException;
import io.aegisops.security.PermissionCodes;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.HandoverRepository;
import io.aegisops.workrecord.domain.model.HandoverStatus;
import io.aegisops.workrecord.domain.model.WorkRecordHandover;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkRecordHandoverService {
  private final HandoverRepository handovers;
  private final WorkRecordQueryService records;

  public WorkRecordHandoverService(HandoverRepository handovers, WorkRecordQueryService records) {
    this.handovers = handovers;
    this.records = records;
  }

  @Transactional
  public WorkRecordHandover create(
      String tenantId, CreateHandoverCommand command, UserPrincipal principal) {
    requirePermission(tenantId, principal);
    if (command == null
        || command.shiftStart() == null
        || command.shiftEnd() == null
        || !command.shiftEnd().isAfter(command.shiftStart())) {
      throw new IllegalArgumentException("shiftEnd must be after shiftStart");
    }
    if (!principal.id().equals(command.fromUserId())) {
      throw new AccessDeniedException("handover sender must be current user");
    }
    if (command.toUserId() == null
        || command.toUserId().isBlank()
        || command.toUserId().equals(command.fromUserId())) {
      throw new IllegalArgumentException("a different receiver is required");
    }
    if (command.summary() == null
        || command.summary().isBlank()
        || command.summary().length() > 10000) {
      throw new IllegalArgumentException("handover summary length must be between 1 and 10000");
    }
    if (command.recordIds().size() > 200 || command.relationIds().size() > 200) {
      throw new IllegalArgumentException("handover supports at most 200 linked items");
    }
    for (String recordId : command.recordIds()) {
      records.get(tenantId, recordId, principal);
    }
    return handovers.create(tenantId, command, principal.id());
  }

  public List<WorkRecordHandover> list(String tenantId, int limit, UserPrincipal principal) {
    requirePermission(tenantId, principal);
    return handovers.listForUser(tenantId, principal.id(), Math.min(Math.max(limit, 1), 200));
  }

  @Transactional
  public WorkRecordHandover submit(
      String tenantId, String handoverId, int rowVersion, UserPrincipal principal) {
    WorkRecordHandover current = requireAccessible(tenantId, handoverId, principal);
    if (!current.createdBy().equals(principal.id()) || current.status() != HandoverStatus.DRAFT) {
      throw new AccessDeniedException("handover cannot be submitted");
    }
    return transition(current, HandoverStatus.SUBMITTED, rowVersion);
  }

  @Transactional
  public WorkRecordHandover accept(
      String tenantId, String handoverId, int rowVersion, UserPrincipal principal) {
    WorkRecordHandover current = requireAccessible(tenantId, handoverId, principal);
    if (!current.toUserId().equals(principal.id())
        || current.status() != HandoverStatus.SUBMITTED) {
      throw new AccessDeniedException("handover cannot be accepted");
    }
    return transition(current, HandoverStatus.ACCEPTED, rowVersion);
  }

  @Transactional
  public WorkRecordHandover complete(
      String tenantId, String handoverId, int rowVersion, UserPrincipal principal) {
    WorkRecordHandover current = requireAccessible(tenantId, handoverId, principal);
    if (!current.toUserId().equals(principal.id()) || current.status() != HandoverStatus.ACCEPTED) {
      throw new AccessDeniedException("handover cannot be completed");
    }
    return transition(current, HandoverStatus.COMPLETED, rowVersion);
  }

  private WorkRecordHandover requireAccessible(
      String tenantId, String id, UserPrincipal principal) {
    requirePermission(tenantId, principal);
    WorkRecordHandover value =
        handovers
            .find(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("handover not found: " + id));
    if (!principal.id().equals(value.fromUserId()) && !principal.id().equals(value.toUserId())) {
      throw new AccessDeniedException("handover is outside current user scope");
    }
    return value;
  }

  private WorkRecordHandover transition(
      WorkRecordHandover current, HandoverStatus target, int rowVersion) {
    if (!handovers.transition(
        current.tenantId(), current.id(), current.status(), target, rowVersion)) {
      throw new ConflictException("handover was modified; refresh and retry");
    }
    return handovers.find(current.tenantId(), current.id()).orElseThrow();
  }

  private static void requirePermission(String tenantId, UserPrincipal principal) {
    if (principal == null
        || !tenantId.equals(principal.tenantId())
        || !principal.hasPermission(PermissionCodes.WORK_RECORD_HANDOVER)) {
      throw new AccessDeniedException("not allowed to manage handover");
    }
  }

  public record CreateHandoverCommand(
      String fromUserId,
      String toUserId,
      OffsetDateTime shiftStart,
      OffsetDateTime shiftEnd,
      String summary,
      List<String> recordIds,
      List<String> relationIds) {
    public CreateHandoverCommand {
      recordIds = recordIds == null ? List.of() : List.copyOf(recordIds);
      relationIds = relationIds == null ? List.of() : List.copyOf(relationIds);
    }
  }
}
