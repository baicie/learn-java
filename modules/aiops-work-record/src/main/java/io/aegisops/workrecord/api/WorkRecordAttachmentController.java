package io.aegisops.workrecord.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.service.UploadSessionService;
import io.aegisops.workrecord.application.service.WorkRecordAttachmentService;
import io.aegisops.workrecord.domain.model.WorkRecordAttachment;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/records/{recordId}/attachments")
@ConditionalOnProperty(
    prefix = "aiops.runtime",
    name = "app",
    havingValue = "app",
    matchIfMissing = true)
public class WorkRecordAttachmentController {
  private final WorkRecordAttachmentService service;

  public WorkRecordAttachmentController(WorkRecordAttachmentService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('work-record:read:self') or hasAuthority('work-record:read:all')")
  public ApiResponse<List<WorkRecordAttachment>> list(
      @PathVariable String recordId, @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(service.list(TenantContext.requireTenantId(), recordId, principal));
  }

  @PostMapping("/uploads")
  @PreAuthorize("hasAuthority('work-record:attachment')")
  public ResponseEntity<ApiResponse<UploadSessionService.PreparedUpload>> prepare(
      @PathVariable String recordId,
      @RequestBody PrepareRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    var prepared =
        service.prepare(
            TenantContext.requireTenantId(),
            recordId,
            new UploadSessionService.PrepareUpload(
                request.fileName(), request.contentType(), request.sizeBytes()),
            principal);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(prepared));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('work-record:attachment')")
  public ApiResponse<WorkRecordAttachment> complete(
      @PathVariable String recordId,
      @RequestBody CompleteRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(
        service.complete(TenantContext.requireTenantId(), recordId, request.uploadId(), principal));
  }

  @PostMapping("/{attachmentId}/download")
  @PreAuthorize("hasAuthority('work-record:read:self') or hasAuthority('work-record:read:all')")
  public ResponseEntity<ApiResponse<WorkRecordAttachmentService.Download>> download(
      @PathVariable String recordId,
      @PathVariable String attachmentId,
      @AuthenticationPrincipal UserPrincipal principal) {
    var download =
        service.download(TenantContext.requireTenantId(), recordId, attachmentId, principal);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(download));
  }

  @DeleteMapping("/{attachmentId}")
  @PreAuthorize("hasAuthority('work-record:attachment')")
  public ApiResponse<Map<String, Boolean>> delete(
      @PathVariable String recordId,
      @PathVariable String attachmentId,
      @AuthenticationPrincipal UserPrincipal principal) {
    service.delete(TenantContext.requireTenantId(), recordId, attachmentId, principal);
    return ApiResponse.ok(Map.of("deleted", true));
  }

  public record PrepareRequest(String fileName, String contentType, long sizeBytes) {}

  public record CompleteRequest(String uploadId) {}
}
