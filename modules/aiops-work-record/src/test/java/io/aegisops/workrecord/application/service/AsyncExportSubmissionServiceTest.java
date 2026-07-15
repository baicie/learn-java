package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.CreateAsyncJobCommand;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.domain.model.AsyncJob;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AsyncExportSubmissionServiceTest {
  @Test
  void createsBoundedExportJobForCurrentUser() {
    AsyncJobService jobs = mock(AsyncJobService.class);
    AsyncJob expected = mock(AsyncJob.class);
    when(jobs.create(any(), any(), any())).thenReturn(expected);
    var service =
        new AsyncExportSubmissionService(
            jobs,
            new ObjectMapper().findAndRegisterModules(),
            Clock.fixed(Instant.parse("2026-07-14T10:00:00Z"), ZoneOffset.UTC));

    assertThat(service.submit("tenant-1", query(), List.of("title"), principal()))
        .isSameAs(expected);
    verify(jobs)
        .create(
            org.mockito.ArgumentMatchers.eq("tenant-1"),
            org.mockito.ArgumentMatchers.eq("user-1"),
            any(CreateAsyncJobCommand.class));
  }

  private static RecordQuery query() {
    return new RecordQuery(
        1, 100, "template-1", null, List.of(), null, null, null, null, null, false, "user-1");
  }

  private static UserPrincipal principal() {
    return new UserPrincipal(
        new UserPrincipal.Identity("user-1", "tenant-1", "alice", "Alice"),
        Set.of(),
        Set.of("work-record:export", "work-record:export:async"),
        Map.of());
  }
}
