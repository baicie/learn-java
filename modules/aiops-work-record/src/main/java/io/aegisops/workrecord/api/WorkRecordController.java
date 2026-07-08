package io.aegisops.workrecord.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.api.PageResult;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.api.dto.CreateWorkRecordRequest;
import io.aegisops.workrecord.api.dto.UpdateWorkRecordRequest;
import io.aegisops.workrecord.api.dto.WorkRecordListRequest;
import io.aegisops.workrecord.application.WorkRecordQueryApplicationService;
import io.aegisops.workrecord.domain.model.WorkRecord;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/records")
public class WorkRecordController {
  private final WorkRecordApplicationService service;
  private final WorkRecordQueryApplicationService queryService;

  public WorkRecordController(
      WorkRecordApplicationService service, WorkRecordQueryApplicationService queryService) {
    this.service = service;
    this.queryService = queryService;
  }

  /** 增强的列表查询接口。 支持内置字段筛选和动态字段筛选，所有状态进入 URL search。 */
  @GetMapping
  @PreAuthorize("hasAuthority('work-record:read:self') or hasAuthority('work-record:read:all')")
  public ApiResponse<PageResult<WorkRecord>> list(
      @Valid @ModelAttribute WorkRecordListRequest request,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(queryService.list(TenantContext.requireTenantId(), user, request));
  }

  /** 列表元数据接口。 返回模板列表、动态列定义和可筛选字段。 */
  @GetMapping("/list-metadata")
  @PreAuthorize("hasAuthority('work-record:read:self') or hasAuthority('work-record:read:all')")
  public ApiResponse<WorkRecordQueryApplicationService.RecordListMetadata> listMetadata(
      @RequestParam(required = false) String templateId) {
    return ApiResponse.ok(queryService.metadata(TenantContext.requireTenantId(), templateId));
  }

  @GetMapping("/{recordId}")
  @PreAuthorize("hasAuthority('work-record:read:self') or hasAuthority('work-record:read:all')")
  public ApiResponse<WorkRecord> get(
      @PathVariable String recordId, @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(service.get(TenantContext.requireTenantId(), recordId, user));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('work-record:write')")
  public ApiResponse<WorkRecord> create(
      @RequestBody CreateWorkRecordRequest request, @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        service.create(
            TenantContext.requireTenantId(), request, user == null ? "system" : user.id()));
  }

  @PutMapping("/{recordId}")
  @PreAuthorize("hasAuthority('work-record:write')")
  public ApiResponse<WorkRecord> update(
      @PathVariable String recordId,
      @RequestBody UpdateWorkRecordRequest request,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(service.update(TenantContext.requireTenantId(), recordId, request, user));
  }

  @DeleteMapping("/{recordId}")
  @PreAuthorize("hasAuthority('work-record:write')")
  public ApiResponse<Void> delete(
      @PathVariable String recordId, @AuthenticationPrincipal UserPrincipal user) {
    service.delete(TenantContext.requireTenantId(), recordId, user);
    return ApiResponse.ok(null);
  }
}
