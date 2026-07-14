package io.aegisops.workrecord.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.ExcelImportJobRequest;
import io.aegisops.workrecord.application.model.ImportedRecordRow;
import io.aegisops.workrecord.application.port.AsyncJobRepository;
import io.aegisops.workrecord.application.port.ObjectStoragePort;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.domain.model.AsyncJob;
import io.aegisops.workrecord.domain.model.AsyncJobStatus;
import io.aegisops.workrecord.domain.model.AsyncJobType;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExcelImportProcessorTest {
  private final AsyncJobRepository jobs = mock(AsyncJobRepository.class);
  private final ObjectStoragePort storage = mock(ObjectStoragePort.class);
  private final WorkRecordFieldIndexRepository fields = mock(WorkRecordFieldIndexRepository.class);
  private final IdempotentImportRowService rows = mock(IdempotentImportRowService.class);
  private final ExcelImportParser parser = mock(ExcelImportParser.class);
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final ExcelImportProcessor processor =
      new ExcelImportProcessor(
          jobs,
          storage,
          new ExcelImportWorkbookReader(fields, parser),
          rows,
          objectMapper,
          Clock.fixed(java.time.Instant.parse("2026-07-14T10:00:00Z"), java.time.ZoneOffset.UTC));

  @Test
  void validatesStoredObjectAndImportsRowsThroughDomainService() throws Exception {
    AsyncJob job = job(requestJson());
    when(jobs.claimQueued(
            org.mockito.ArgumentMatchers.eq("tenant-1"),
            org.mockito.ArgumentMatchers.eq("job-1"),
            any()))
        .thenReturn(Optional.of(job));
    when(storage.stat("tenant-1/imports/upload-1/source.xlsx"))
        .thenReturn(
            new ObjectStoragePort.StoredObject(
                "tenant-1/imports/upload-1/source.xlsx", 1024, null));
    when(storage.get("tenant-1/imports/upload-1/source.xlsx", 1024))
        .thenReturn(new ByteArrayInputStream(new byte[0]));
    when(fields.listByVersion("tenant-1", "version-1")).thenReturn(List.of());
    when(parser.parseAll(any(), any(), any()))
        .thenReturn(
            new ExcelImportParser.ParseResult(
                List.of(
                    new ImportedRecordRow(
                        2,
                        "完成发布",
                        "draft",
                        null,
                        OffsetDateTime.parse("2026-07-14T09:00Z"),
                        Map.of())),
                List.of()));
    when(jobs.complete(any(), any(), any(), any())).thenReturn(true);

    processor.process("tenant-1", "job-1", principal());

    verify(rows)
        .importOnce(
            org.mockito.ArgumentMatchers.eq("tenant-1"),
            org.mockito.ArgumentMatchers.eq("job-1"),
            org.mockito.ArgumentMatchers.eq(2),
            any(),
            any());
    verify(jobs)
        .complete(
            org.mockito.ArgumentMatchers.eq("tenant-1"),
            org.mockito.ArgumentMatchers.eq("job-1"),
            any(),
            any());
  }

  private String requestJson() throws Exception {
    return objectMapper.writeValueAsString(
        new ExcelImportJobRequest(
            "template-1",
            "version-1",
            "tenant-1/imports/upload-1/source.xlsx",
            "records.xlsx",
            "application/octet-stream",
            1024,
            "draft",
            null,
            null,
            false));
  }

  private static UserPrincipal principal() {
    return new UserPrincipal(
        new UserPrincipal.Identity("user-1", "tenant-1", "alice", "Alice"),
        Set.of(),
        Set.of("work-record:import", "work-record:write"),
        Map.of());
  }

  private static AsyncJob job(String requestJson) {
    OffsetDateTime now = OffsetDateTime.parse("2026-07-14T10:00Z");
    return new AsyncJob(
        "job-1",
        "tenant-1",
        AsyncJobType.EXCEL_IMPORT,
        AsyncJobStatus.PROCESSING,
        "user-1",
        requestJson,
        "{}",
        "tenant-1/imports/upload-1/source.xlsx",
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
}
