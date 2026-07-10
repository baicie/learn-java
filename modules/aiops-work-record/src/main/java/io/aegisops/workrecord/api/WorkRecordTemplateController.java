package io.aegisops.workrecord.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.api.dto.TemplateRequests.CopyTemplateRequest;
import io.aegisops.workrecord.api.dto.TemplateRequests.CreateTemplateRequest;
import io.aegisops.workrecord.api.dto.TemplateRequests.PublishTemplateRequest;
import io.aegisops.workrecord.api.dto.TemplateRequests.UpdateTemplateDraftRequest;
import io.aegisops.workrecord.api.dto.TemplateRequests.UpdateTemplateRequest;
import io.aegisops.workrecord.application.command.CopyTemplateCommand;
import io.aegisops.workrecord.application.command.CreateTemplateCommand;
import io.aegisops.workrecord.application.command.PublishTemplateCommand;
import io.aegisops.workrecord.application.command.TemplatePublishValidationResult;
import io.aegisops.workrecord.application.command.UpdateTemplateCommand;
import io.aegisops.workrecord.application.command.UpdateTemplateDraftCommand;
import io.aegisops.workrecord.application.service.WorkRecordTemplateService;
import io.aegisops.workrecord.application.service.WorkRecordTemplateVersionService;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import io.aegisops.workrecord.domain.model.WorkRecordTemplateVersion;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/work-record/templates")
public class WorkRecordTemplateController {
  private final WorkRecordTemplateService templateService;
  private final WorkRecordTemplateVersionService versionService;

  public WorkRecordTemplateController(
      WorkRecordTemplateService templateService, WorkRecordTemplateVersionService versionService) {
    this.templateService = templateService;
    this.versionService = versionService;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('work-record:template:read')")
  public ApiResponse<List<WorkRecordTemplate>> list(
      @RequestParam(defaultValue = "false") boolean includeDisabled) {
    return ApiResponse.ok(templateService.list(TenantContext.requireTenantId(), includeDisabled));
  }

  @GetMapping("/{templateId}")
  @PreAuthorize("hasAuthority('work-record:template:read')")
  public ApiResponse<WorkRecordTemplate> get(@PathVariable String templateId) {
    return ApiResponse.ok(templateService.get(TenantContext.requireTenantId(), templateId));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('work-record:template:write')")
  public ApiResponse<WorkRecordTemplate> create(
      @RequestBody CreateTemplateRequest request, @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        templateService.create(
            TenantContext.requireTenantId(),
            new CreateTemplateCommand(
                request.code(),
                request.name(),
                request.description(),
                request.schemaJson(),
                request.designerJson()),
            actorId(user)));
  }

  @PutMapping("/{templateId}")
  @PreAuthorize("hasAuthority('work-record:template:write')")
  public ApiResponse<WorkRecordTemplate> update(
      @PathVariable String templateId,
      @RequestBody UpdateTemplateRequest request,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        templateService.update(
            TenantContext.requireTenantId(),
            templateId,
            new UpdateTemplateCommand(request.name(), request.description()),
            actorId(user)));
  }

  @PutMapping("/{templateId}/draft")
  @PreAuthorize("hasAuthority('work-record:template:write')")
  public ApiResponse<WorkRecordTemplate> updateDraft(
      @PathVariable String templateId,
      @RequestBody UpdateTemplateDraftRequest request,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        templateService.updateDraft(
            TenantContext.requireTenantId(),
            templateId,
            new UpdateTemplateDraftCommand(
                request.name(),
                request.description(),
                request.schemaJson(),
                request.designerJson()),
            actorId(user)));
  }

  @PostMapping("/{templateId}/enable")
  @PreAuthorize("hasAuthority('work-record:template:write')")
  public ApiResponse<WorkRecordTemplate> enable(
      @PathVariable String templateId, @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        templateService.enable(TenantContext.requireTenantId(), templateId, actorId(user)));
  }

  @PostMapping("/{templateId}/disable")
  @PreAuthorize("hasAuthority('work-record:template:write')")
  public ApiResponse<WorkRecordTemplate> disable(
      @PathVariable String templateId, @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        templateService.disable(TenantContext.requireTenantId(), templateId, actorId(user)));
  }

  @PostMapping("/{templateId}/archive")
  @PreAuthorize("hasAuthority('work-record:template:write')")
  public ApiResponse<WorkRecordTemplate> archive(
      @PathVariable String templateId, @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        templateService.archive(TenantContext.requireTenantId(), templateId, actorId(user)));
  }

  @PostMapping("/{templateId}/copy")
  @PreAuthorize("hasAuthority('work-record:template:write')")
  public ApiResponse<WorkRecordTemplate> copy(
      @PathVariable String templateId,
      @RequestBody CopyTemplateRequest request,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        templateService.copy(
            TenantContext.requireTenantId(),
            new CopyTemplateCommand(
                templateId, request.targetCode(), request.targetName(), request.description()),
            actorId(user)));
  }

  @PostMapping("/{templateId}/validate-publish")
  @PreAuthorize("hasAuthority('work-record:template:write')")
  public ApiResponse<TemplatePublishValidationResult> validatePublish(
      @PathVariable String templateId) {
    return ApiResponse.ok(
        versionService.validatePublish(TenantContext.requireTenantId(), templateId));
  }

  @PostMapping("/{templateId}/publish")
  @PreAuthorize("hasAuthority('work-record:template:write')")
  public ApiResponse<WorkRecordTemplateVersion> publish(
      @PathVariable String templateId,
      @RequestBody PublishTemplateRequest request,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        versionService.publish(
            TenantContext.requireTenantId(),
            new PublishTemplateCommand(templateId, request.versionName()),
            actorId(user)));
  }

  @GetMapping("/{templateId}/versions")
  @PreAuthorize("hasAuthority('work-record:template:read')")
  public ApiResponse<List<WorkRecordTemplateVersion>> listVersions(
      @PathVariable String templateId) {
    return ApiResponse.ok(versionService.list(TenantContext.requireTenantId(), templateId));
  }

  @GetMapping("/{templateId}/versions/{versionId}")
  @PreAuthorize("hasAuthority('work-record:template:read')")
  public ApiResponse<WorkRecordTemplateVersion> getVersion(
      @PathVariable String templateId, @PathVariable String versionId) {
    return ApiResponse.ok(
        versionService.get(TenantContext.requireTenantId(), templateId, versionId));
  }

  @GetMapping("/{templateId}/versions/{versionId}/fields")
  @PreAuthorize("hasAuthority('work-record:template:read')")
  public ApiResponse<List<WorkRecordField>> listVersionFields(
      @PathVariable String templateId, @PathVariable String versionId) {
    return ApiResponse.ok(
        versionService.fields(TenantContext.requireTenantId(), templateId, versionId));
  }

  private String actorId(UserPrincipal user) {
    return user == null || user.id() == null || user.id().isBlank() ? "system" : user.id();
  }
}
