package io.aegisops.workrecord.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.security.PermissionCodes;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.AsyncExportJobRequest;
import io.aegisops.workrecord.application.port.AsyncJobRepository;
import io.aegisops.workrecord.application.port.AsyncJobRepository.JobCompletion;
import io.aegisops.workrecord.application.port.AsyncJobRepository.JobProgress;
import io.aegisops.workrecord.application.port.ObjectStoragePort;
import io.aegisops.workrecord.domain.model.AsyncJob;
import io.aegisops.workrecord.domain.model.AsyncJobStatus;
import io.aegisops.workrecord.domain.model.AsyncJobType;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "aiops.runtime", name = "app", havingValue = "worker")
public class AsyncExportProcessor {
  private static final long MAX_FILE_BYTES = 100L * 1024L * 1024L;
  private static final String CONTENT_TYPE = "text/csv;charset=UTF-8";

  private final AsyncJobRepository jobs;
  private final ObjectStoragePort storage;
  private final WorkRecordExportService exports;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public AsyncExportProcessor(
      AsyncJobRepository jobs,
      ObjectStoragePort storage,
      WorkRecordExportService exports,
      ObjectMapper objectMapper,
      @Qualifier("workRecordClock") Clock clock) {
    this.jobs = jobs;
    this.storage = storage;
    this.exports = exports;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public void process(String tenantId, String jobId, UserPrincipal principal) {
    requirePrincipal(tenantId, principal);
    AsyncJob job = jobs.claimQueued(tenantId, jobId, now()).orElse(null);
    if (job == null) {
      AsyncJob existing = jobs.findById(tenantId, jobId).orElse(null);
      if (existing != null && isTerminal(existing.status())) {
        return;
      }
      if (existing == null || existing.status() != AsyncJobStatus.PROCESSING) {
        throw new IllegalStateException("asynchronous export job is not runnable");
      }
      job = existing;
    }
    String objectKey = tenantId + "/exports/" + jobId + "/work-records.csv";
    try {
      validateJob(job, principal);
      AsyncExportJobRequest request =
          objectMapper.readValue(job.requestJson(), AsyncExportJobRequest.class);
      validateRequest(request, principal);
      ExportedFile exported = streamToStorage(tenantId, jobId, request, principal, objectKey);
      String fileName = "work-records-" + jobId + ".csv";
      JobProgress progress =
          new JobProgress(exported.rowCount(), exported.rowCount(), exported.rowCount(), 0);
      String resultJson =
          objectMapper.writeValueAsString(
              Map.of(
                  "rowCount", exported.rowCount(),
                  "sizeBytes", exported.sizeBytes(),
                  "sha256", exported.sha256()));
      if (!jobs.complete(
          tenantId,
          jobId,
          new JobCompletion(
              AsyncJobStatus.SUCCEEDED,
              resultJson,
              objectKey,
              fileName,
              CONTENT_TYPE,
              null,
              progress),
          now())) {
        throw new IllegalStateException("asynchronous export completion state changed");
      }
    } catch (Exception ex) {
      deleteQuietly(objectKey);
      jobs.fail(tenantId, jobId, safeMessage(ex), now());
    }
  }

  private ExportedFile streamToStorage(
      String tenantId,
      String jobId,
      AsyncExportJobRequest request,
      UserPrincipal principal,
      String objectKey)
      throws Exception {
    try (PipedInputStream input = new PipedInputStream(64 * 1024);
        PipedOutputStream output = new PipedOutputStream(input)) {
      CompletableFuture<Integer> rows = new CompletableFuture<>();
      Thread.startVirtualThread(
          () -> {
            try (BufferedWriter writer =
                new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
              var result =
                  exports.streamCsv(
                      tenantId,
                      request.query(),
                      request.columns(),
                      principal,
                      writer,
                      request.maxRows(),
                      count ->
                          jobs.updateProgress(
                              tenantId, jobId, new JobProgress(count, count, count, 0), now()));
              rows.complete(result.rowCount());
            } catch (Throwable ex) {
              rows.completeExceptionally(ex);
            }
          });
      ObjectStoragePort.StoredObject stored =
          storage.putUnknownLength(objectKey, CONTENT_TYPE, input, MAX_FILE_BYTES);
      int rowCount = rows.get();
      return new ExportedFile(rowCount, stored.sizeBytes(), stored.sha256());
    }
  }

  private static void validateJob(AsyncJob job, UserPrincipal principal) {
    if (job.jobType() != AsyncJobType.EXCEL_EXPORT || !job.requestedBy().equals(principal.id())) {
      throw new AccessDeniedException("asynchronous export job owner mismatch");
    }
  }

  private static void validateRequest(AsyncExportJobRequest request, UserPrincipal principal) {
    if (request == null
        || request.query() == null
        || request.maxRows() < 1
        || request.maxRows() > AsyncExportSubmissionService.MAX_ROWS
        || !principal.id().equals(request.query().currentUserId())) {
      throw new IllegalArgumentException("invalid asynchronous export request");
    }
  }

  private static void requirePrincipal(String tenantId, UserPrincipal principal) {
    if (principal == null
        || !tenantId.equals(principal.tenantId())
        || !principal.hasPermission(PermissionCodes.WORK_RECORD_EXPORT_ASYNC)
        || !principal.hasPermission(PermissionCodes.WORK_RECORD_EXPORT)) {
      throw new AccessDeniedException("asynchronous export permission was revoked");
    }
  }

  private static boolean isTerminal(AsyncJobStatus status) {
    return status == AsyncJobStatus.SUCCEEDED
        || status == AsyncJobStatus.PARTIALLY_SUCCEEDED
        || status == AsyncJobStatus.FAILED
        || status == AsyncJobStatus.CANCELLED
        || status == AsyncJobStatus.EXPIRED;
  }

  private void deleteQuietly(String objectKey) {
    try {
      storage.delete(objectKey);
    } catch (RuntimeException ignored) {
      // The upload can fail before the object exists.
    }
  }

  private OffsetDateTime now() {
    return OffsetDateTime.now(clock);
  }

  private static String safeMessage(Throwable ex) {
    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
    String message = cause.getMessage();
    if (message == null || message.isBlank()) {
      return cause.getClass().getSimpleName();
    }
    String sanitized = message.replaceAll("[\\r\\n\\t]+", " ").trim();
    return sanitized.length() <= 500 ? sanitized : sanitized.substring(0, 500);
  }

  private record ExportedFile(int rowCount, long sizeBytes, String sha256) {}
}
