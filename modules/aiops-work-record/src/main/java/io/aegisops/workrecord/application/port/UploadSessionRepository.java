package io.aegisops.workrecord.application.port;

import io.aegisops.workrecord.domain.model.UploadSession;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface UploadSessionRepository {
  UploadSession insert(UploadSession session);

  Optional<UploadSession> consumePrepared(
      String tenantId, String id, String requestedBy, String purpose, OffsetDateTime now);
}
