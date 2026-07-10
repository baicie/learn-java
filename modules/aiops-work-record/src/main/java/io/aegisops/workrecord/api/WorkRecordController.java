package io.aegisops.workrecord.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.api.dto.RecordRequests.CreateRecordRequest;
import io.aegisops.workrecord.api.dto.RecordRequests.RecordQueryRequest;
import io.aegisops.workrecord.api.dto.RecordRequests.UpdateRecordRequest;
import io.aegisops.workrecord.application.command.CreateRecordCommand;
import io.aegisops.workrecord.application.command.RecordDynamicFilter;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.command.UpdateRecordCommand;
import io.aegisops.workrecord.application.service.WorkRecordListMetaService;
import io.aegisops.workrecord.application.service.WorkRecordQueryService;
import io.aegisops.workrecord.application.service.WorkRecordService;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/work-record/records")
public class WorkRecordController {
  private final WorkRecordService recordService;
  private final WorkRecordQueryService queryService;
  private final WorkRecordListMetaService metaService;
  private final ObjectMapper objectMapper;

  public WorkRecordController(
      WorkRecordService recordService,
      WorkRecordQueryService queryService,
      WorkRecordListMetaService metaService,
      ObjectMapper objectMapper) {
    this.recordService = recordService;
    this.queryService = queryService;
    this.metaService = metaService;
    this.objectMapper = objectMapper;
  }

  @GetMapping("/meta")
  @PreAuthorize("hasAuthority('work-record:read:all') or hasAuthority('work-record:read:self')")
  public ApiResponse<?> meta(
      @RequestParam(required = false) String templateId) {
    return ApiResponse.ok(
        metaService.meta(
            TenantContext.requireTenantId(),
            templateId));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('work-record:read:all') or hasAuthority('work-record:read:self')")
  public ApiResponse<?> page(
      @ModelAttribute RecordQueryRequest request, @AuthenticationPrincipal UserPrincipal user) {
    RecordQuery query =
        new RecordQuery(
            request.page() != null ? request.page() : 1,
            request.pageSize() != null ? request.pageSize() : 20,
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
    return ApiResponse.ok(queryService.page(TenantContext.requireTenantId(), query, user));
  }

  @GetMapping("/{recordId}")
  @PreAuthorize("hasAuthority('work-record:read:all') or hasAuthority('work-record:read:self')")
  public ApiResponse<WorkRecord> get(
      @PathVariable String recordId, @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(queryService.get(TenantContext.requireTenantId(), recordId, user));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('work-record:write')")
  public ApiResponse<WorkRecord> create(
      @RequestBody CreateRecordRequest request, @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        recordService.create(
            TenantContext.requireTenantId(),
            new CreateRecordCommand(
                request.templateId(),
                request.templateVersionId(),
                request.title(),
                request.status(),
                request.ownerId(),
                parseRequiredRecordTime(request.recordTime()),
                request.builtinDataJson(),
                request.customDataJson()),
            user));
  }

  @PutMapping("/{recordId}")
  @PreAuthorize("hasAuthority('work-record:write')")
  public ApiResponse<WorkRecord> update(
      @PathVariable String recordId,
      @RequestBody UpdateRecordRequest request,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        recordService.update(
            TenantContext.requireTenantId(),
            recordId,
            new UpdateRecordCommand(
                request.title(),
                request.status(),
                request.ownerId(),
                parseOptionalRecordTime(request.recordTime()),
                request.builtinDataJson(),
                request.customDataJson()),
            user));
  }

  @DeleteMapping("/{recordId}")
  @PreAuthorize("hasAuthority('work-record:delete')")
  public ApiResponse<Void> delete(
      @PathVariable String recordId, @AuthenticationPrincipal UserPrincipal user) {
    recordService.delete(TenantContext.requireTenantId(), recordId, user);
    return ApiResponse.ok(null);
  }

  OffsetDateTime parseRequiredRecordTime(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("recordTime is required");
    }
    return parseOffsetRecordTime(value);
  }

  OffsetDateTime parseOptionalRecordTime(String value) {
    if (value == null) {
      return null;
    }
    if (value.isBlank()) {
      throw new IllegalArgumentException("recordTime must not be blank");
    }
    return parseOffsetRecordTime(value);
  }

  OffsetDateTime parseOffsetRecordTime(String value) {
    try {
      return OffsetDateTime.parse(value);
    } catch (DateTimeParseException ex) {
      throw new IllegalArgumentException("recordTime must be ISO offset datetime", ex);
    }
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