package io.aegisops.workrecord.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.security.PermissionCodes;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.CreateRecordCommand;
import io.aegisops.workrecord.application.command.ExcelImportJobRequest;
import io.aegisops.workrecord.application.model.ImportedRecordRow;
import io.aegisops.workrecord.application.port.AsyncJobRepository;
import io.aegisops.workrecord.application.port.AsyncJobRepository.JobCompletion;
import io.aegisops.workrecord.application.port.AsyncJobRepository.JobProgress;
import io.aegisops.workrecord.application.port.ObjectStoragePort;
import io.aegisops.workrecord.application.service.ExcelImportParser.ParseResult;
import io.aegisops.workrecord.domain.model.AsyncJob;
import io.aegisops.workrecord.domain.model.AsyncJobStatus;
import io.aegisops.workrecord.domain.model.AsyncJobType;
import java.io.InputStream;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "aiops.runtime", name = "app", havingValue = "app")
public class ExcelImportProcessor {
  private static final int PROGRESS_INTERVAL = 25;

  private final AsyncJobRepository jobs;
  private final ObjectStoragePort storage;
  private final ExcelImportWorkbookReader workbookReader;
  private final IdempotentImportRowService rowImporter;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public ExcelImportProcessor(
      AsyncJobRepository jobs,
      ObjectStoragePort storage,
      ExcelImportWorkbookReader workbookReader,
      IdempotentImportRowService rowImporter,
      ObjectMapper objectMapper,
      @Qualifier("workRecordClock") Clock clock) {
    this.jobs = jobs;
    this.storage = storage;
    this.workbookReader = workbookReader;
    this.rowImporter = rowImporter;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public void process(String tenantId, String jobId, UserPrincipal principal) {
    requirePrincipal(tenantId, principal);
    AsyncJob job = jobs.claimQueued(tenantId, jobId, now()).orElse(null);
    if (job == null) {
      job =
          jobs.findById(tenantId, jobId)
              .filter(existing -> existing.status() == AsyncJobStatus.PROCESSING)
              .orElseThrow(() -> new IllegalStateException("Excel import job is not runnable"));
    }
    try {
      if (job.jobType() != AsyncJobType.EXCEL_IMPORT || !job.requestedBy().equals(principal.id())) {
        throw new AccessDeniedException("Excel import job owner mismatch");
      }
      ExcelImportJobRequest request =
          objectMapper.readValue(job.requestJson(), ExcelImportJobRequest.class);
      validateRequest(tenantId, job, request);
      var source = storage.stat(request.sourceObjectKey());
      if (source.sizeBytes() != request.declaredSizeBytes()) {
        throw new IllegalArgumentException("uploaded Excel size does not match declaration");
      }

      ParseResult parsed;
      try (InputStream input =
          storage.get(request.sourceObjectKey(), request.declaredSizeBytes())) {
        parsed = workbookReader.read(tenantId, request, input);
      }
      importRows(tenantId, jobId, principal, request, parsed);
    } catch (Exception ex) {
      jobs.fail(tenantId, jobId, safeMessage(ex), now());
    }
  }

  private void importRows(
      String tenantId,
      String jobId,
      UserPrincipal principal,
      ExcelImportJobRequest request,
      ParseResult parsed)
      throws Exception {
    List<Map<String, Object>> failures = new ArrayList<>();
    parsed
        .failures()
        .forEach(
            failure -> {
              Map<String, Object> detail = new LinkedHashMap<>();
              detail.put("rowNumber", failure.rowNumber());
              detail.put("errorCode", "INVALID_ROW");
              detail.put("message", failure.message());
              if (failure.fieldCode() != null) {
                detail.put("fieldCode", failure.fieldCode());
              }
              failures.add(Map.copyOf(detail));
            });
    List<ImportedRecordRow> rows = parsed.rows();
    int total = rows.size() + failures.size();
    int success = 0;
    int processed = failures.size();
    if (request.stopOnError() && !failures.isEmpty()) {
      completeImport(tenantId, jobId, total, processed, success, failures);
      return;
    }
    for (ImportedRecordRow row : rows) {
      try {
        rowImporter.importOnce(
            tenantId,
            jobId,
            row.rowNumber(),
            new CreateRecordCommand(
                request.templateId(),
                request.templateVersionId(),
                row.title(),
                row.status(),
                row.ownerId(),
                row.recordTime(),
                "{}",
                objectMapper.writeValueAsString(row.customData())),
            principal);
        success++;
      } catch (RuntimeException ex) {
        failures.add(
            Map.of(
                "rowNumber", row.rowNumber(),
                "errorCode", ex.getClass().getSimpleName(),
                "message", safeMessage(ex)));
      }
      processed++;
      JobProgress progress = new JobProgress(total, processed, success, failures.size());
      if (processed % PROGRESS_INTERVAL == 0 || processed == total) {
        jobs.updateProgress(tenantId, jobId, progress, now());
      }
      if (request.stopOnError() && !failures.isEmpty()) {
        break;
      }
    }

    completeImport(tenantId, jobId, total, processed, success, failures);
  }

  private void completeImport(
      String tenantId,
      String jobId,
      int total,
      int processed,
      int success,
      List<Map<String, Object>> failures)
      throws Exception {
    JobProgress finalProgress = new JobProgress(total, processed, success, failures.size());
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("successCount", success);
    result.put("failureCount", failures.size());
    result.put("failures", failures);
    AsyncJobStatus status =
        failures.isEmpty()
            ? AsyncJobStatus.SUCCEEDED
            : success == 0 ? AsyncJobStatus.FAILED : AsyncJobStatus.PARTIALLY_SUCCEEDED;
    if (!jobs.complete(
        tenantId,
        jobId,
        new JobCompletion(
            status, objectMapper.writeValueAsString(result), null, null, null, null, finalProgress),
        now())) {
      throw new IllegalStateException("Excel import job completion state changed");
    }
  }

  private static void validateRequest(
      String tenantId, AsyncJob job, ExcelImportJobRequest request) {
    if (request == null
        || request.sourceObjectKey() == null
        || !request.sourceObjectKey().equals(job.sourceObjectKey())
        || !request.sourceObjectKey().startsWith(tenantId + "/imports/")
        || request.declaredSizeBytes() < 1
        || request.declaredSizeBytes() > 20L * 1024L * 1024L) {
      throw new IllegalArgumentException("invalid Excel import job request");
    }
  }

  private static void requirePrincipal(String tenantId, UserPrincipal principal) {
    if (principal == null
        || !tenantId.equals(principal.tenantId())
        || !principal.hasPermission(PermissionCodes.WORK_RECORD_IMPORT)) {
      throw new AccessDeniedException("Excel import permission was revoked");
    }
  }

  private OffsetDateTime now() {
    return OffsetDateTime.now(clock);
  }

  private static String safeMessage(Throwable ex) {
    String message = ex.getMessage();
    if (message == null || message.isBlank()) {
      return ex.getClass().getSimpleName();
    }
    String sanitized = message.replaceAll("[\\r\\n\\t]+", " ").trim();
    return sanitized.length() <= 500 ? sanitized : sanitized.substring(0, 500);
  }
}
