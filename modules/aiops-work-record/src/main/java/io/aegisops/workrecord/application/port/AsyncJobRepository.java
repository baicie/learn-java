package io.aegisops.workrecord.application.port;

import io.aegisops.common.api.PageResult;
import io.aegisops.workrecord.domain.model.AsyncJob;
import io.aegisops.workrecord.domain.model.AsyncJobStatus;
import io.aegisops.workrecord.domain.model.AsyncJobType;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface AsyncJobRepository {
  PageResult<AsyncJob> page(
      String tenantId,
      String requestedBy,
      AsyncJobStatus status,
      AsyncJobType jobType,
      int page,
      int size);

  Optional<AsyncJob> findById(String tenantId, String id);

  Optional<AsyncJob> cancelQueued(String tenantId, String id, OffsetDateTime cancelledAt);

  AsyncJob insert(AsyncJob job);

  Optional<AsyncJob> claimQueued(String tenantId, String id, OffsetDateTime startedAt);

  boolean updateProgress(String tenantId, String id, JobProgress progress, OffsetDateTime now);

  boolean complete(String tenantId, String id, JobCompletion completion, OffsetDateTime now);

  boolean fail(String tenantId, String id, String errorMessage, OffsetDateTime now);

  record JobProgress(int totalCount, int processedCount, int successCount, int failureCount) {}

  record JobCompletion(
      AsyncJobStatus status,
      String resultJson,
      String resultObjectKey,
      String resultFileName,
      String resultContentType,
      String errorObjectKey,
      JobProgress progress) {}
}
