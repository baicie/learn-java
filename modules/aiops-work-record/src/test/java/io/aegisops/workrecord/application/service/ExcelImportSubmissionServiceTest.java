package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.SubmitExcelImportCommand;
import io.aegisops.workrecord.application.port.WorkRecordTemplateVersionRepository;
import io.aegisops.workrecord.domain.model.AsyncJob;
import io.aegisops.workrecord.domain.model.AsyncJobStatus;
import io.aegisops.workrecord.domain.model.AsyncJobType;
import io.aegisops.workrecord.domain.model.UploadSession;
import io.aegisops.workrecord.domain.model.WorkRecordTemplateVersion;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExcelImportSubmissionServiceTest {
  private final UploadSessionService uploads = mock(UploadSessionService.class);
  private final AsyncJobService jobs = mock(AsyncJobService.class);
  private final WorkRecordTemplateVersionRepository versions =
      mock(WorkRecordTemplateVersionRepository.class);
  private final ExcelImportSubmissionService service =
      new ExcelImportSubmissionService(
          uploads,
          jobs,
          versions,
          new ObjectMapper(),
          Clock.fixed(java.time.Instant.parse("2026-07-14T10:00:00Z"), java.time.ZoneOffset.UTC));

  @Test
  void validatesVersionThenConsumesUploadAndCreatesImportJob() {
    var command =
        new SubmitExcelImportCommand(
            "upload-1", "template-1", "version-1", "draft", null, null, false);
    when(versions.findByTemplateAndVersion("tenant-1", "template-1", "version-1"))
        .thenReturn(Optional.of(version()));
    when(uploads.consumeExcelImport(
            org.mockito.ArgumentMatchers.eq("tenant-1"),
            org.mockito.ArgumentMatchers.eq("upload-1"),
            any()))
        .thenReturn(upload());
    when(jobs.create(
            org.mockito.ArgumentMatchers.eq("tenant-1"),
            org.mockito.ArgumentMatchers.eq("user-1"),
            any()))
        .thenReturn(job());

    assertThat(service.submit("tenant-1", command, principal()).id()).isEqualTo("job-1");

    verify(versions).findByTemplateAndVersion("tenant-1", "template-1", "version-1");
    verify(uploads)
        .consumeExcelImport(
            org.mockito.ArgumentMatchers.eq("tenant-1"),
            org.mockito.ArgumentMatchers.eq("upload-1"),
            any());
  }

  private static UserPrincipal principal() {
    return new UserPrincipal(
        new UserPrincipal.Identity("user-1", "tenant-1", "alice", "Alice"),
        Set.of(),
        Set.of("work-record:import"),
        Map.of());
  }

  private static WorkRecordTemplateVersion version() {
    OffsetDateTime now = OffsetDateTime.parse("2026-07-14T10:00Z");
    return new WorkRecordTemplateVersion(
        "version-1", "tenant-1", "template-1", 1, "v1", "{}", "{}", "[]", "admin", now, now);
  }

  private static UploadSession upload() {
    OffsetDateTime now = OffsetDateTime.parse("2026-07-14T10:00Z");
    return new UploadSession(
        "upload-1",
        "tenant-1",
        "user-1",
        "excel_import",
        "tenant-1/imports/upload-1/source.xlsx",
        "records.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        1024,
        "consumed",
        now.plusMinutes(10),
        now,
        now,
        now);
  }

  private static AsyncJob job() {
    OffsetDateTime now = OffsetDateTime.parse("2026-07-14T10:00Z");
    return new AsyncJob(
        "job-1",
        "tenant-1",
        AsyncJobType.EXCEL_IMPORT,
        AsyncJobStatus.QUEUED,
        "user-1",
        "{}",
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
        0,
        null,
        null,
        now.plusDays(7),
        now,
        now);
  }
}
