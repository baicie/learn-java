package io.aegisops.workrecord.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.api.dto.TemplateRequests.CreateTemplateRequest;
import io.aegisops.workrecord.api.dto.TemplateRequests.PublishTemplateRequest;
import io.aegisops.workrecord.api.dto.TemplateRequests.UpdateTemplateDraftRequest;
import io.aegisops.workrecord.application.command.CreateTemplateCommand;
import io.aegisops.workrecord.application.command.PublishTemplateCommand;
import io.aegisops.workrecord.application.command.UpdateTemplateDraftCommand;
import io.aegisops.workrecord.application.service.WorkRecordTemplateService;
import io.aegisops.workrecord.application.service.WorkRecordTemplateVersionService;
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
      WorkRecordTemplateService templateService,
      WorkRecordTemplateVersionService versionService) {
    this.templateService = templateService;
    this.versionService = versionService;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('work-record:template:read')")
  public ApiResponse<List<WorkRecordTemplate>> list() {
    return ApiResponse.ok(templateService.list(TenantContext.requireTenantId()));
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
            user == null ? "system" : user.id()));
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
                request.name(), request.description(), request.schemaJson(), request.designerJson()),
            user == null ? "system" : user.id()));
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
            user == null ? "system" : user.id()));
  }

  @DeleteMapping("/{templateId}")
  @PreAuthorize("hasAuthority('work-record:template:write')")
  public ApiResponse<Void> disable(
      @PathVariable String templateId, @AuthenticationPrincipal UserPrincipal user) {
    templateService.disable(
        TenantContext.requireTenantId(), templateId, user == null ? "system" : user.id());
    return ApiResponse.ok(null);
  }
}
