package io.aegisops.workrecord.api;

import io.aegisops.workrecord.domain.model.AsyncJob;
import io.aegisops.workrecord.domain.model.AsyncJobStatus;
import io.aegisops.workrecord.domain.model.AsyncJobType;
import java.time.OffsetDateTime;

public record AsyncJobResponse(
    String id,
    AsyncJobType jobType,
    AsyncJobStatus status,
    String requestedBy,
    String requestJson,
    String resultJson,
    int totalCount,
    int processedCount,
    int successCount,
    int failureCount,
    String resultFileName,
    String resultContentType,
    String errorMessage,
    long rowVersion,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    OffsetDateTime expiresAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static AsyncJobResponse from(AsyncJob job) {
    return new AsyncJobResponse(
        job.id(),
        job.jobType(),
        job.status(),
        job.requestedBy(),
        job.requestJson(),
        job.resultJson(),
        job.totalCount(),
        job.processedCount(),
        job.successCount(),
        job.failureCount(),
        job.resultFileName(),
        job.resultContentType(),
        job.errorMessage(),
        job.rowVersion(),
        job.startedAt(),
        job.finishedAt(),
        job.expiresAt(),
        job.createdAt(),
        job.updatedAt());
  }
}
