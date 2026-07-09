package io.aegisops.workrecord.application.port;

public interface WorkRecordTemplateUsageRepository {
  long countRecordsByTemplate(String tenantId, String templateId);

  long countRecordsByTemplateVersion(String tenantId, String templateVersionId);
}