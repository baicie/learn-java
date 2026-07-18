package io.aegisops.workrecord.application.service;

import io.aegisops.common.api.PageResult;
import io.aegisops.common.exception.ConflictException;
import io.aegisops.common.exception.ResourceNotFoundException;
import io.aegisops.common.id.Ids;
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
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AsyncJobService {
  private final AsyncJobRepository repository;
  private final OutboxWriter outboxWriter;
  private final Clock clock;

  public AsyncJobService(
      AsyncJobRepository repository,
      OutboxWriter outboxWriter,
      @Qualifier("workRecordClock") Clock clock) {
    this.repository = repository;
    this.outboxWriter = outboxWriter;
    this.clock = clock;
  }

  @Transactional
  public AsyncJob create(String tenantId, String requestedBy, CreateAsyncJobCommand command) {
    requireText(tenantId, "tenantId");
    String safeRequester = requireUser(requestedBy);
    if (command == null) {
      throw new IllegalArgumentException("async job command is required");
    }
    OffsetDateTime now = OffsetDateTime.now(clock);
    if (command.expiresAt() != null && !command.expiresAt().isAfter(now)) {
      throw new IllegalArgumentException("async job expiry must be in the future");
    }
    String id = Ids.newId();
    AsyncJob job =
        repository.insert(
            new AsyncJob(
                id,
                tenantId,
                command.jobType(),
                AsyncJobStatus.QUEUED,
                safeRequester,
                command.requestJson(),
                "{}",
                command.sourceObjectKey(),
                0,
                0,
                0,
                0,
                null,
                null,
                null,
                null,
                "async-job:" + id,
                null,
                0,
                null,
                null,
                command.expiresAt(),
                now,
                now));
    outboxWriter.enqueue(
        new OutboxMessage(
            tenantId,
            "worker",
            command.outboxJobName(),
            Map.of("tenantId", tenantId, "jobId", id, "requestedBy", safeRequester),
            "async-job:" + id,
            5,
            now));
    return job;
  }

  public PageResult<AsyncJob> page(String tenantId, AsyncJobQuery query) {
    int safePage = PageResult.normalizePage(query.page());
    int safeSize = PageResult.normalizeSize(query.size());
    return repository.page(
        tenantId,
        query.readAll() ? null : requireUser(query.userId()),
        parseStatus(query.status()),
        parseType(query.jobType()),
        safePage,
        safeSize);
  }

  public AsyncJob get(String tenantId, String id, String userId, boolean readAll) {
    AsyncJob job =
        repository
            .findById(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("async job not found: " + id));
    assertVisible(job, userId, readAll);
    return job;
  }

  public AsyncJob cancel(String tenantId, String id, UserPrincipal principal) {
    if (principal == null || !tenantId.equals(principal.tenantId())) {
      throw new AccessDeniedException("authenticated tenant user is required");
    }
    AsyncJob job =
        get(tenantId, id, principal.id(), principal.hasPermission("work-record:read:all"));
    boolean allowed =
        switch (job.jobType()) {
          case EXCEL_IMPORT -> principal.hasPermission("work-record:import");
          case EXCEL_EXPORT ->
              principal.hasPermission("work-record:export")
                  && principal.hasPermission("work-record:export:async");
          default -> false;
        };
    if (!allowed) {
      throw new AccessDeniedException("async job action permission is required");
    }
    if (job.status() != AsyncJobStatus.QUEUED) {
      throw new ConflictException("only queued async jobs can be cancelled");
    }
    return repository
        .cancelQueued(tenantId, id, OffsetDateTime.now(clock))
        .orElseThrow(() -> new ConflictException("async job state changed; refresh and retry"));
  }

  private static void assertVisible(AsyncJob job, String userId, boolean readAll) {
    if (!readAll && !job.requestedBy().equals(requireUser(userId))) {
      // Do not reveal whether another user's task exists.
      throw new ResourceNotFoundException("async job not found: " + job.id());
    }
  }

  private static String requireUser(String userId) {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("authenticated user is required");
    }
    return userId;
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
  }

  private static AsyncJobStatus parseStatus(String value) {
    return value == null || value.isBlank() ? null : AsyncJobStatus.fromStorage(value);
  }

  private static AsyncJobType parseType(String value) {
    return value == null || value.isBlank() ? null : AsyncJobType.fromStorage(value);
  }
}
