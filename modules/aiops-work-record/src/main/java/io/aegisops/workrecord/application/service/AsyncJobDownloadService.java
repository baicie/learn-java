package io.aegisops.workrecord.application.service;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.ObjectStorageUrlSigner;
import io.aegisops.workrecord.domain.model.AsyncJob;
import io.aegisops.workrecord.domain.model.AsyncJobStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class AsyncJobDownloadService {
  private static final Duration URL_EXPIRY = Duration.ofMinutes(5);

  private final AsyncJobService jobs;
  private final ObjectStorageUrlSigner signer;
  private final Clock clock;

  public AsyncJobDownloadService(
      AsyncJobService jobs,
      ObjectStorageUrlSigner signer,
      @Qualifier("workRecordClock") Clock clock) {
    this.jobs = jobs;
    this.signer = signer;
    this.clock = clock;
  }

  public Download download(String tenantId, String jobId, UserPrincipal principal) {
    if (principal == null || !tenantId.equals(principal.tenantId())) {
      throw new IllegalArgumentException("authenticated tenant user is required");
    }
    AsyncJob job =
        jobs.get(tenantId, jobId, principal.id(), principal.hasPermission("work-record:read:all"));
    if (job.status() != AsyncJobStatus.SUCCEEDED
        && job.status() != AsyncJobStatus.PARTIALLY_SUCCEEDED) {
      throw new IllegalStateException("async job result is not ready");
    }
    OffsetDateTime now = OffsetDateTime.now(clock);
    if (job.expiresAt() != null && !job.expiresAt().isAfter(now)) {
      throw new IllegalStateException("async job result has expired");
    }
    if (job.resultObjectKey() == null
        || !job.resultObjectKey().startsWith(tenantId + "/")
        || job.resultFileName() == null
        || job.resultContentType() == null) {
      throw new IllegalStateException("async job has no downloadable result");
    }
    return new Download(
        signer.presignedGet(job.resultObjectKey(), URL_EXPIRY),
        job.resultFileName(),
        job.resultContentType(),
        now.plus(URL_EXPIRY));
  }

  public record Download(
      String url, String fileName, String contentType, OffsetDateTime expiresAt) {}
}
