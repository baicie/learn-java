package io.aegisops.workrecord.application.service;

import io.aegisops.common.exception.ResourceNotFoundException;
import io.aegisops.security.PermissionCodes;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.RelationTargetPort;
import io.aegisops.workrecord.application.port.WorkRecordRelationRepository;
import io.aegisops.workrecord.domain.model.RelationType;
import io.aegisops.workrecord.domain.model.WorkRecordRelation;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkRecordRelationService {
  private final WorkRecordQueryService records;
  private final WorkRecordPermissionService permissions;
  private final RelationTargetPort targets;
  private final WorkRecordRelationRepository relations;
  private final WorkRecordAuditService audit;

  public WorkRecordRelationService(
      WorkRecordQueryService records,
      WorkRecordPermissionService permissions,
      RelationTargetPort targets,
      WorkRecordRelationRepository relations,
      WorkRecordAuditService audit) {
    this.records = records;
    this.permissions = permissions;
    this.targets = targets;
    this.relations = relations;
    this.audit = audit;
  }

  public List<WorkRecordRelation> list(String tenantId, String recordId, UserPrincipal principal) {
    records.get(tenantId, recordId, principal);
    return relations.list(tenantId, recordId);
  }

  @Transactional
  public WorkRecordRelation create(
      String tenantId,
      String recordId,
      RelationType type,
      String targetId,
      UserPrincipal principal) {
    requirePermission(tenantId, principal);
    var record = records.get(tenantId, recordId, principal);
    permissions.requireEdit(principal, record);
    var relation =
        relations.create(
            tenantId,
            recordId,
            type,
            targets.resolve(tenantId, type, targetId, principal),
            principal.id());
    audit.record(
        tenantId,
        recordId,
        record.templateId(),
        "work_record_relation",
        relation.id(),
        "work_record.relation.create",
        principal.id(),
        relation.snapshotJson());
    return relation;
  }

  @Transactional
  public void delete(String tenantId, String recordId, String relationId, UserPrincipal principal) {
    requirePermission(tenantId, principal);
    var record = records.get(tenantId, recordId, principal);
    permissions.requireEdit(principal, record);
    if (!relations.delete(tenantId, recordId, relationId)) {
      throw new ResourceNotFoundException("relation not found: " + relationId);
    }
    audit.record(
        tenantId,
        recordId,
        record.templateId(),
        "work_record_relation",
        relationId,
        "work_record.relation.delete",
        principal.id(),
        "{\"recordId\":\"" + recordId + "\"}");
  }

  private static void requirePermission(String tenantId, UserPrincipal principal) {
    if (principal == null
        || !tenantId.equals(principal.tenantId())
        || !principal.hasPermission(PermissionCodes.WORK_RECORD_RELATION)) {
      throw new AccessDeniedException("not allowed to manage record relations");
    }
  }
}
