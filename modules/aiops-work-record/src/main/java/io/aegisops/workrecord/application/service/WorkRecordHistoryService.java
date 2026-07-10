package io.aegisops.workrecord.application.service;

import io.aegisops.audit.AuditEvent;
import io.aegisops.audit.AuditQueryService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the change history of a single work record by reading the unified {@code audit_log}
 * filtered to {@code resourceType = "work_record"}. The caller is responsible for verifying the
 * user can actually read the record before invoking this service.
 */
@Service
public class WorkRecordHistoryService {
  private static final int HISTORY_LIMIT = 100;
  private static final String RECORD_RESOURCE_TYPE = "work_record";

  private final AuditQueryService auditQueryService;

  public WorkRecordHistoryService(AuditQueryService auditQueryService) {
    this.auditQueryService = auditQueryService;
  }

  @Transactional(readOnly = true)
  public List<AuditEvent> list(String tenantId, String recordId) {
    return auditQueryService.listByResource(
        tenantId, RECORD_RESOURCE_TYPE, recordId, HISTORY_LIMIT);
  }
}
