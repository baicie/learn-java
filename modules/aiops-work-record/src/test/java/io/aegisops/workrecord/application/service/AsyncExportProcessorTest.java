package io.aegisops.workrecord.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.AsyncExportJobRequest;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.port.AsyncJobRepository;
import io.aegisops.workrecord.application.port.ObjectStoragePort;
import io.aegisops.workrecord.domain.model.AsyncJob;
import io.aegisops.workrecord.domain.model.AsyncJobStatus;
import io.aegisops.workrecord.domain.model.AsyncJobType;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AsyncExportProcessorTest {
  @Test
  void streamsCsvToObjectStorageAndCompletesJob() throws Exception {
    AsyncJobRepository jobs = mock(AsyncJobRepository.class);
    ObjectStoragePort storage = mock(ObjectStoragePort.class);
    WorkRecordExportService exports = mock(WorkRecordExportService.class);
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    AsyncJob job = job(mapper.writeValueAsString(request()));
    when(jobs.claimQueued(any(), any(), any())).thenReturn(Optional.of(job));
    when(exports.streamCsv(any(), any(), any(), any(), any(), any(Integer.class), any()))
        .thenAnswer(
            invocation -> {
              Appendable output = invocation.getArgument(4);
              output.append("\uFEFF\"标题\"\r\n\"日报\"\r\n");
              return new WorkRecordExportService.StreamingExportResult(1);
            });
    when(storage.putUnknownLength(any(), any(), any(), any(Long.class)))
        .thenAnswer(
            invocation -> {
              byte[] content = ((java.io.InputStream) invocation.getArgument(2)).readAllBytes();
              return new ObjectStoragePort.StoredObject(
                  invocation.getArgument(0), content.length, "abc123");
            });
    when(jobs.complete(any(), any(), any(), any())).thenReturn(true);
    var processor =
        new AsyncExportProcessor(
            jobs,
            storage,
            exports,
            mapper,
            Clock.fixed(Instant.parse("2026-07-14T10:00:00Z"), ZoneOffset.UTC));

    processor.process("tenant-1", "job-1", principal());

    verify(storage)
        .putUnknownLength(
            org.mockito.ArgumentMatchers.eq("tenant-1/exports/job-1/work-records.csv"),
            org.mockito.ArgumentMatchers.eq("text/csv;charset=UTF-8"),
            any(),
            org.mockito.ArgumentMatchers.eq(100L * 1024L * 1024L));
    verify(jobs).complete(any(), any(), any(), any());
  }

  private static AsyncExportJobRequest request() {
    return new AsyncExportJobRequest(
        new RecordQuery(
            1, 100, "template-1", null, List.of(), null, null, null, null, null, false, "user-1"),
        List.of("title"),
        100_000);
  }

  private static AsyncJob job(String requestJson) {
    OffsetDateTime now = OffsetDateTime.parse("2026-07-14T10:00Z");
    return new AsyncJob(
        "job-1",
        "tenant-1",
        AsyncJobType.EXCEL_EXPORT,
        AsyncJobStatus.PROCESSING,
        "user-1",
        requestJson,
        "{}",
        null,
        0,
        0,
        0,
        0,
        null,
        null,
        null,
        null,
        "async-job:job-1",
        null,
        1,
        now,
        null,
        now.plusDays(7),
        now,
        now);
  }

  private static UserPrincipal principal() {
    return new UserPrincipal(
        new UserPrincipal.Identity("user-1", "tenant-1", "alice", "Alice"),
        Set.of(),
        Set.of("work-record:export", "work-record:export:async"),
        Map.of());
  }
}
