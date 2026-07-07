package io.aegisops.workrecord;

import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/records")
public class WorkRecordExportController {
  private static final MediaType TEXT_CSV = new MediaType("text", "csv");

  private final WorkRecordExportService service;

  public WorkRecordExportController(WorkRecordExportService service) {
    this.service = service;
  }

  @GetMapping("/export")
  @PreAuthorize("hasAuthority('work-record:export')")
  public ResponseEntity<byte[]> export(
      @RequestParam(required = false) String ownerId,
      @RequestParam(required = false) String status,
      @AuthenticationPrincipal UserPrincipal user) {
    byte[] body = service.exportCsv(TenantContext.requireTenantId(), ownerId, status, user);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=work-records.csv")
        .contentType(TEXT_CSV)
        .body(body);
  }
}
