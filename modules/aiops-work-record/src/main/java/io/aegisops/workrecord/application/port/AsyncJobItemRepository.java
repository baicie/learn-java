package io.aegisops.workrecord.application.port;

import java.time.OffsetDateTime;

public interface AsyncJobItemRepository {
  boolean begin(String tenantId, String jobId, String itemKey, int rowNumber, OffsetDateTime now);

  boolean succeed(
      String tenantId, String jobId, String itemKey, String resourceId, OffsetDateTime now);
}
