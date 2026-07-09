package io.aegisops.workrecord.application.port;

public interface WorkRecordAuditRepository {
  void record(
      String tenantId,
      String recordId,
      String templateId,
      String resourceType,
      String resourceId,
      String action,
      String actorId,
      String beforeJson,
      String afterJson,
      String detailJson);
}
