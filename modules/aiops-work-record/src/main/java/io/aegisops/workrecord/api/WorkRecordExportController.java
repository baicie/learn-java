package io.aegisops.workrecord.api;

import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.api.dto.RecordRequests.RecordQueryRequest;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.service.WorkRecordExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/work-record")
public class WorkRecordExportController {
  private final WorkRecordExportService exportService;

  public WorkRecordExportController(WorkRecordExportService exportService) {
    this.exportService = exportService;
  }

  @PostMapping("/export")
  @PreAuthorize("hasAuthority('work-record:export')")
  public byte[] exportCsv(
      @RequestBody RecordQueryRequest request, @AuthenticationPrincipal UserPrincipal user) {
    RecordQuery query =
        new RecordQuery(
            request.page() != null ? request.page() : 1,
            request.pageSize() != null ? request.pageSize() : 5000,
            request.templateId(),
            request.templateVersionId(),
            request.statuses(),
            request.keyword(),
            request.recordTimeFrom(),
            request.recordTimeTo(),
            request.creatorId(),
            request.ownerId(),
            false,
            user == null ? null : user.id());

    byte[] csv = exportService.exportCsv(TenantContext.requireTenantId(), query, user);

    return csv;
  }
}
