package io.aegisops.workrecord.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.api.dto.RecordRequests.ExportRecordRequest;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.service.AsyncExportSubmissionService;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/async-exports")
@ConditionalOnProperty(
    prefix = "aiops.runtime",
    name = "app",
    havingValue = "server",
    matchIfMissing = true)
public class AsyncExportController {
  private final AsyncExportSubmissionService submissions;

  public AsyncExportController(AsyncExportSubmissionService submissions) {
    this.submissions = submissions;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('work-record:export:async') and hasAuthority('work-record:export')")
  public ApiResponse<Map<String, String>> submit(
      @RequestBody ExportRecordRequest request, @AuthenticationPrincipal UserPrincipal user) {
    RecordQuery query =
        new RecordQuery(
            1,
            100,
            request.templateId(),
            request.templateVersionId(),
            request.statuses(),
            request.keyword(),
            request.recordTimeFrom(),
            request.recordTimeTo(),
            request.creatorId(),
            request.ownerId(),
            false,
            user.id(),
            request.dynamicFilters() == null ? List.of() : request.dynamicFilters(),
            normalizeSortBy(request.sortBy()),
            normalizeSortDir(request.sortDir()),
            request.quickView(),
            request.workdayCount(),
            request.recordIds());
    var job = submissions.submit(TenantContext.requireTenantId(), query, request.columns(), user);
    return ApiResponse.ok(Map.of("jobId", job.id()));
  }

  private static String normalizeSortBy(String value) {
    return value == null || value.isBlank() ? "recordTime" : value;
  }

  private static String normalizeSortDir(String value) {
    return value == null || value.isBlank() ? "desc" : value;
  }
}
