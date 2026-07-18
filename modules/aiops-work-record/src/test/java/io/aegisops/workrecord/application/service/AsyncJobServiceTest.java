package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.common.api.PageResult;
import io.aegisops.common.exception.ConflictException;
import io.aegisops.common.exception.ResourceNotFoundException;
import io.aegisops.common.outbox.OutboxMessage;
import io.aegisops.common.outbox.OutboxWriter;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.AsyncJobQuery;
import io.aegisops.workrecord.application.command.CreateAsyncJobCommand;
import io.aegisops.workrecord.application.port.AsyncJobRepository;
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

class AsyncJobServiceTest {

  private final AsyncJobRepository repository = mock(AsyncJobRepository.class);
  private final OutboxWriter outboxWriter = mock(OutboxWriter.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-07-14T10:00:00Z"), ZoneOffset.UTC);
  private final AsyncJobService service = new AsyncJobService(repository, outboxWriter, clock);

  @Test
  void createsJobAndOutboxInOneApplicationTransaction() {
    CreateAsyncJobCommand command =
        new CreateAsyncJobCommand(
            AsyncJobType.EXCEL_IMPORT,
            "{\"templateId\":\"template-1\"}",
            "tenant-1/imports/job-1/source.xlsx",
            OffsetDateTime.parse("2026-07-21T10:00Z"),
            "work-record-excel-import");
    when(repository.insert(org.mockito.ArgumentMatchers.any(AsyncJob.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    AsyncJob job = service.create("tenant-1", "user-1", command);

    assertThat(job.jobType()).isEqualTo(AsyncJobType.EXCEL_IMPORT);
    assertThat(job.status()).isEqualTo(AsyncJobStatus.QUEUED);
    verify(outboxWriter).enqueue(org.mockito.ArgumentMatchers.any(OutboxMessage.class));
  }

  @Test
  void selfReaderIsAlwaysScopedToRequester() {
    when(repository.page("tenant-1", "user-1", null, null, 1, 20))
        .thenReturn(new PageResult<>(1, 1, 20, List.of(job(AsyncJobStatus.QUEUED))));

    PageResult<AsyncJob> result =
        service.page("tenant-1", new AsyncJobQuery("user-1", false, null, null, 1, 20));

    assertThat(result.total()).isEqualTo(1);
    verify(repository).page("tenant-1", "user-1", null, null, 1, 20);
  }

  @Test
  void cannotReadAnotherUsersJobWithoutReadAll() {
    when(repository.findById("tenant-1", "job-1"))
        .thenReturn(Optional.of(job(AsyncJobStatus.QUEUED)));

    assertThatThrownBy(() -> service.get("tenant-1", "job-1", "user-2", false))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void cancelUsesAtomicQueuedStateTransition() {
    AsyncJob cancelled = job(AsyncJobStatus.CANCELLED);
    when(repository.findById("tenant-1", "job-1"))
        .thenReturn(Optional.of(job(AsyncJobStatus.QUEUED)));
    when(repository.cancelQueued("tenant-1", "job-1", OffsetDateTime.parse("2026-07-14T10:00Z")))
        .thenReturn(Optional.of(cancelled));

    assertThat(
            service
                .cancel(
                    "tenant-1",
                    "job-1",
                    principal(
                        Set.of(
                            "work-record:read:self",
                            "work-record:export",
                            "work-record:export:async")))
                .status())
        .isEqualTo(AsyncJobStatus.CANCELLED);
  }

  @Test
  void completedJobCannotBeCancelled() {
    when(repository.findById("tenant-1", "job-1"))
        .thenReturn(Optional.of(job(AsyncJobStatus.SUCCEEDED)));

    assertThatThrownBy(
            () ->
                service.cancel(
                    "tenant-1",
                    "job-1",
                    principal(
                        Set.of(
                            "work-record:read:self",
                            "work-record:export",
                            "work-record:export:async"))))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void readerCannotCancelExportJob() {
    when(repository.findById("tenant-1", "job-1"))
        .thenReturn(Optional.of(job(AsyncJobStatus.QUEUED)));

    assertThatThrownBy(
            () -> service.cancel("tenant-1", "job-1", principal(Set.of("work-record:read:self"))))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test
  void unsupportedJobTypeFailsClosed() {
    when(repository.findById("tenant-1", "job-1"))
        .thenReturn(Optional.of(job(AsyncJobStatus.QUEUED, AsyncJobType.AI_SUMMARY)));

    assertThatThrownBy(
            () ->
                service.cancel(
                    "tenant-1",
                    "job-1",
                    principal(
                        Set.of(
                            "work-record:read:self",
                            "work-record:export",
                            "work-record:export:async"))))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  private static UserPrincipal principal(Set<String> permissions) {
    return new UserPrincipal(
        new UserPrincipal.Identity("user-1", "tenant-1", "alice", "Alice"),
        Set.of(),
        permissions,
        Map.of());
  }

  private static AsyncJob job(AsyncJobStatus status) {
    return job(status, AsyncJobType.EXCEL_EXPORT);
  }

  private static AsyncJob job(AsyncJobStatus status, AsyncJobType type) {
    OffsetDateTime now = OffsetDateTime.parse("2026-07-14T09:00Z");
    return new AsyncJob(
        "job-1",
        "tenant-1",
        type,
        status,
        "user-1",
        "{}",
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
        null,
        null,
        0,
        null,
        null,
        null,
        now,
        now);
  }
}
