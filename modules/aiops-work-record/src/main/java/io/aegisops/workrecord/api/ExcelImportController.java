package io.aegisops.workrecord.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.SubmitExcelImportCommand;
import io.aegisops.workrecord.application.service.ExcelImportSubmissionService;
import io.aegisops.workrecord.application.service.UploadSessionService;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/imports")
@ConditionalOnProperty(
    prefix = "aiops.runtime",
    name = "app",
    havingValue = "server",
    matchIfMissing = true)
public class ExcelImportController {
  private final UploadSessionService uploads;
  private final ExcelImportSubmissionService submissions;

  public ExcelImportController(
      UploadSessionService uploads, ExcelImportSubmissionService submissions) {
    this.uploads = uploads;
    this.submissions = submissions;
  }

  @PostMapping("/uploads")
  @PreAuthorize("hasAuthority('work-record:import')")
  public ResponseEntity<ApiResponse<UploadSessionService.PreparedUpload>> prepareUpload(
      @RequestBody PrepareUploadRequest request, @AuthenticationPrincipal UserPrincipal user) {
    var prepared =
        uploads.prepareExcelImport(
            TenantContext.requireTenantId(),
            user,
            new UploadSessionService.PrepareUpload(
                request.originalFileName(), request.contentType(), request.sizeBytes()));
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(prepared));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('work-record:import')")
  public ApiResponse<Map<String, String>> submit(
      @RequestBody SubmitImportRequest request, @AuthenticationPrincipal UserPrincipal user) {
    var job =
        submissions.submit(
            TenantContext.requireTenantId(),
            new SubmitExcelImportCommand(
                request.uploadId(),
                request.templateId(),
                request.templateVersionId(),
                request.defaultStatus(),
                request.defaultOwnerId(),
                request.defaultRecordTime(),
                request.stopOnError()),
            user);
    return ApiResponse.ok(Map.of("jobId", job.id()));
  }

  public record PrepareUploadRequest(String originalFileName, String contentType, long sizeBytes) {}

  public record SubmitImportRequest(
      String uploadId,
      String templateId,
      String templateVersionId,
      String defaultStatus,
      String defaultOwnerId,
      OffsetDateTime defaultRecordTime,
      boolean stopOnError) {}
}
