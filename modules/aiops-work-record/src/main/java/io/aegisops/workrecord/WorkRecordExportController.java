package io.aegisops.workrecord;

import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/records")
public class WorkRecordExportController {
  private static final MediaType TEXT_CSV = new MediaType("text", "csv");

  private final WorkRecordExportService service;

  public WorkRecordExportController(WorkRecordExportService service) {
    this.service = service;
  }

  @PostMapping("/export")
  @PreAuthorize("hasAuthority('work-record:export')")
  public ResponseEntity<byte[]> export(
      @Valid @RequestBody WorkRecordExportRequest request,
      @AuthenticationPrincipal UserPrincipal user) {
    byte[] body = service.exportCsv(
        TenantContext.requireTenantId(),
        request.templateId(),
        request.status(),
        request.keyword(),
        request.recordTimeFrom(),
        request.recordTimeTo(),
        request.filters(),
        request.columns(),
        user);
    String filename = "work-records-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
        .contentType(TEXT_CSV)
        .body(body);
  }
}
