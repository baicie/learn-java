package io.aegisops.workrecord.application.port;

import io.aegisops.workrecord.domain.model.UploadSession;
import io.aegisops.workrecord.domain.model.WorkRecordAttachment;
import java.util.List;
import java.util.Optional;

public interface WorkRecordAttachmentRepository {
  List<WorkRecordAttachment> list(String tenantId, String recordId);

  Optional<WorkRecordAttachment> find(String tenantId, String id);

  WorkRecordAttachment create(
      String tenantId, String recordId, UploadSession upload, String uploadedBy);

  boolean markReady(String tenantId, String id, String sha256);

  boolean quarantine(String tenantId, String id);

  boolean markDeleted(String tenantId, String id);
}
