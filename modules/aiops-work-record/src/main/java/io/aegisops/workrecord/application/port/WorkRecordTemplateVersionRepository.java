package io.aegisops.workrecord.application.port;

import io.aegisops.workrecord.domain.model.WorkRecordTemplateVersion;
import java.util.List;
import java.util.Optional;

public interface WorkRecordTemplateVersionRepository {
  int nextVersionNo(String tenantId, String templateId);

  WorkRecordTemplateVersion create(
      String tenantId,
      String templateId,
      int versionNo,
      String versionName,
      String schemaJson,
      String designerJson,
      String fieldIndexJson,
      String actor);

  Optional<WorkRecordTemplateVersion> find(String tenantId, String versionId);

  Optional<WorkRecordTemplateVersion> findCurrent(String tenantId, String templateId);

  List<WorkRecordTemplateVersion> list(String tenantId, String templateId);
}
