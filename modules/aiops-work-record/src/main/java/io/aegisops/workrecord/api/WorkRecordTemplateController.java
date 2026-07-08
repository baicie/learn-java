package io.aegisops.workrecord.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.api.dto.CreateFieldRequest;
import io.aegisops.workrecord.api.dto.CreateTemplateRequest;
import io.aegisops.workrecord.api.dto.TemplateSchemaRequest;
import io.aegisops.workrecord.api.dto.UpdateFieldRequest;
import io.aegisops.workrecord.api.dto.UpdateTemplateRequest;
import io.aegisops.workrecord.application.WorkRecordTemplateApplicationService;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/templates")
public class WorkRecordTemplateController {
  private final WorkRecordTemplateApplicationService service;

  public WorkRecordTemplateController(WorkRecordTemplateApplicationService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('work-record:template:read')")
  public ApiResponse<List<WorkRecordTemplate>> list() {
    return ApiResponse.ok(service.listTemplates(TenantContext.requireTenantId()));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('work-record:template:write')")
  public ApiResponse<WorkRecordTemplate> create(
      @RequestBody CreateTemplateRequest request, @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        service.createTemplate(
            TenantContext.requireTenantId(), request, user == null ? "system" : user.id()));
  }

  @PutMapping("/{templateId}")
  @PreAuthorize("hasAuthority('work-record:template:write')")
  public ApiResponse<WorkRecordTemplate> update(
      @PathVariable String templateId,
      @RequestBody UpdateTemplateRequest request,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        service.updateTemplate(
            TenantContext.requireTenantId(),
            templateId,
            request,
            user == null ? "system" : user.id()));
  }

  @PostMapping("/{templateId}/schema")
  @PreAuthorize("hasAuthority('work-record:template:write')")
  public ApiResponse<WorkRecordTemplate> saveSchema(
      @PathVariable String templateId,
      @RequestBody TemplateSchemaRequest request,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        service.saveSchema(
            TenantContext.requireTenantId(),
            templateId,
            request,
            user == null ? "system" : user.id()));
  }

  @GetMapping("/{templateId}/fields")
  @PreAuthorize("hasAuthority('work-record:template:read')")
  public ApiResponse<List<WorkRecordField>> listFields(@PathVariable String templateId) {
    return ApiResponse.ok(service.listFields(TenantContext.requireTenantId(), templateId));
  }

  @PostMapping("/{templateId}/fields")
  @PreAuthorize("hasAuthority('work-record:template:write')")
  public ApiResponse<WorkRecordField> createField(
      @PathVariable String templateId,
      @RequestBody CreateFieldRequest request,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        service.createField(
            TenantContext.requireTenantId(),
            templateId,
            request,
            user == null ? "system" : user.id()));
  }

  @PutMapping("/{templateId}/fields/{fieldId}")
  @PreAuthorize("hasAuthority('work-record:template:write')")
  public ApiResponse<WorkRecordField> updateField(
      @PathVariable String templateId,
      @PathVariable String fieldId,
      @RequestBody UpdateFieldRequest request,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        service.updateField(
            TenantContext.requireTenantId(),
            templateId,
            fieldId,
            request,
            user == null ? "system" : user.id()));
  }
}
