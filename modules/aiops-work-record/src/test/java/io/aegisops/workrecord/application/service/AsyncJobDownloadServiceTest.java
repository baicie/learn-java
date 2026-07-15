package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.ObjectStorageUrlSigner;
import io.aegisops.workrecord.domain.model.AsyncJob;
import io.aegisops.workrecord.domain.model.AsyncJobStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AsyncJobDownloadServiceTest {
  @Test
  void signsTenantScopedSuccessfulResultWithoutExposingObjectKey() {
    AsyncJobService jobs = mock(AsyncJobService.class);
    ObjectStorageUrlSigner signer = mock(ObjectStorageUrlSigner.class);
    when(jobs.get("tenant-1", "job-1", "user-1", false)).thenReturn(job());
    when(signer.presignedGet(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn("https://storage.example/download");
    var service =
        new AsyncJobDownloadService(
            jobs, signer, Clock.fixed(Instant.parse("2026-07-14T10:00:00Z"), ZoneOffset.UTC));

    var result = service.download("tenant-1", "job-1", principal());

    assertThat(result.url()).isEqualTo("https://storage.example/download");
    assertThat(result.fileName()).isEqualTo("records.csv");
  }

  private static AsyncJob job() {
    OffsetDateTime now = OffsetDateTime.parse("2026-07-14T10:00Z");
    return new AsyncJob(
        "job-1",
        "tenant-1",
        io.aegisops.workrecord.domain.model.AsyncJobType.EXCEL_EXPORT,
        AsyncJobStatus.SUCCEEDED,
        "user-1",
        "{}",
        "{}",
        null,
        1,
        1,
        1,
        0,
        "tenant-1/exports/job-1/records.csv",
        "records.csv",
        "text/csv",
        null,
        "async-job:job-1",
        null,
        1,
        now,
        now,
        now.plusDays(7),
        now,
        now);
  }

  private static UserPrincipal principal() {
    return new UserPrincipal(
        new UserPrincipal.Identity("user-1", "tenant-1", "alice", "Alice"),
        Set.of(),
        Set.of("work-record:read:self"),
        Map.of());
  }
}
