package io.aegisops.workrecord.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.ResourceNotFoundException;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.CreateAsyncJobCommand;
import io.aegisops.workrecord.application.command.ExcelImportJobRequest;
import io.aegisops.workrecord.application.command.SubmitExcelImportCommand;
import io.aegisops.workrecord.application.port.WorkRecordTemplateVersionRepository;
import io.aegisops.workrecord.domain.model.AsyncJob;
import io.aegisops.workrecord.domain.model.AsyncJobType;
import io.aegisops.workrecord.domain.model.UploadSession;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExcelImportSubmissionService {
  private final UploadSessionService uploads;
  private final AsyncJobService jobs;
  private final WorkRecordTemplateVersionRepository versions;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public ExcelImportSubmissionService(
      UploadSessionService uploads,
      AsyncJobService jobs,
      WorkRecordTemplateVersionRepository versions,
      ObjectMapper objectMapper,
      @Qualifier("workRecordClock") Clock clock) {
    this.uploads = uploads;
    this.jobs = jobs;
    this.versions = versions;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Transactional
  public AsyncJob submit(String tenantId, SubmitExcelImportCommand command, UserPrincipal user) {
    validate(command);
    versions
        .findByTemplateAndVersion(tenantId, command.templateId(), command.templateVersionId())
        .orElseThrow(() -> new ResourceNotFoundException("work record template version not found"));
    UploadSession upload = uploads.consumeExcelImport(tenantId, command.uploadId(), user);
    ExcelImportJobRequest request =
        new ExcelImportJobRequest(
            command.templateId(),
            command.templateVersionId(),
            upload.objectKey(),
            upload.originalFileName(),
            upload.contentType(),
            upload.declaredSizeBytes(),
            normalizeStatus(command.defaultStatus()),
            blankToNull(command.defaultOwnerId()),
            command.defaultRecordTime(),
            command.stopOnError());
    return jobs.create(
        tenantId,
        user.id(),
        new CreateAsyncJobCommand(
            AsyncJobType.EXCEL_IMPORT,
            toJson(request),
            upload.objectKey(),
            OffsetDateTime.now(clock).plusDays(7),
            "work-record-excel-import"));
  }

  private static void validate(SubmitExcelImportCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("import command is required");
    }
    requireText(command.uploadId(), "uploadId");
    requireText(command.templateId(), "templateId");
    requireText(command.templateVersionId(), "templateVersionId");
  }

  private String toJson(ExcelImportJobRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("failed to serialize Excel import request", ex);
    }
  }

  private static String normalizeStatus(String status) {
    return status == null || status.isBlank() ? "draft" : status.trim();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
  }
}
