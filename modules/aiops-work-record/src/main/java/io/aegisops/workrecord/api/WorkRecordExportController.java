package io.aegisops.workrecord.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.api.dto.RecordRequests.RecordQueryRequest;
import io.aegisops.workrecord.application.command.RecordDynamicFilter;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.service.WorkRecordExportService;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
  private final WorkRecordExportService exportService;
  private final ObjectMapper objectMapper;

  public WorkRecordExportController(
      WorkRecordExportService exportService, ObjectMapper objectMapper) {
    this.exportService = exportService;
    this.objectMapper = objectMapper;
  }

  @PostMapping("/export")
  @PreAuthorize("hasAuthority('work-record:export')")
  public ResponseEntity<byte[]> exportCsv(
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
            user == null ? null : user.id(),
            parseDynamicFilters(request.dynamicFilters()),
            normalizeSortBy(request.sortBy()),
            normalizeSortDir(request.sortDir()),
            request.quickView(),
            request.workdayCount());

    byte[] csv = exportService.exportCsv(TenantContext.requireTenantId(), query, user);

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"work-records.csv\"")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csv);
  }

  private List<RecordDynamicFilter> parseDynamicFilters(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(raw, new TypeReference<List<RecordDynamicFilter>>() {});
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid dynamicFilters", ex);
    }
  }

  private String normalizeSortBy(String value) {
    if (value == null || value.isBlank()) {
      return "recordTime";
    }
    return value;
  }

  private String normalizeSortDir(String value) {
    if ("asc".equalsIgnoreCase(value)) {
      return "asc";
    }
    return "desc";
  }
}
