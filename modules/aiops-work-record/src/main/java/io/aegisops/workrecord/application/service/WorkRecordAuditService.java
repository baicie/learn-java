package io.aegisops.workrecord.application.service;

import io.aegisops.workrecord.application.port.WorkRecordAuditRepository;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordAuditService {
  private final WorkRecordAuditRepository repository;

  public WorkRecordAuditService(WorkRecordAuditRepository repository) {
    this.repository = repository;
  }

  public void record(
      String tenantId,
      String recordId,
      String templateId,
      String resourceType,
      String resourceId,
      String action,
      String actorId,
      String detailJson) {
    repository.record(
        tenantId,
        recordId,
        templateId,
        resourceType,
        resourceId,
        action,
        actorId == null || actorId.isBlank() ? "system" : actorId,
        "{}",
        "{}",
        detailJson == null || detailJson.isBlank() ? "{}" : detailJson);
  }
}
