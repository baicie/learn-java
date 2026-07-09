package io.aegisops.workrecord.application.service;

import io.aegisops.common.api.PageResult;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.port.WorkRecordRepository;
import io.aegisops.workrecord.domain.model.WorkRecord;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordQueryService {
  private final WorkRecordRepository repository;
  private final WorkRecordPermissionService permissionService;

  public WorkRecordQueryService(
      WorkRecordRepository repository, WorkRecordPermissionService permissionService) {
    this.repository = repository;
    this.permissionService = permissionService;
  }

  public PageResult<WorkRecord> page(String tenantId, RecordQuery query, UserPrincipal user) {
    boolean onlySelf = !permissionService.canReadAll(user);
    if (onlySelf && !permissionService.canReadSelf(user)) {
      throw new SecurityException("not allowed to read work records");
    }
    RecordQuery effective =
        new RecordQuery(
            Math.max(1, query.page()),
            Math.min(Math.max(1, query.pageSize()), 200),
            query.templateId(),
            query.templateVersionId(),
            query.statuses(),
            query.keyword(),
            query.recordTimeFrom(),
            query.recordTimeTo(),
            query.creatorId(),
            query.ownerId(),
            onlySelf,
            user == null ? null : user.id());
    return repository.page(tenantId, effective);
  }

  public WorkRecord get(String tenantId, String recordId, UserPrincipal user) {
    WorkRecord record =
        repository
            .find(tenantId, recordId)
            .orElseThrow(() -> new IllegalArgumentException("work record not found"));
    permissionService.requireRead(user, record);
    return record;
  }
}
