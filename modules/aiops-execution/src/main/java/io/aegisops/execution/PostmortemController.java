package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.execution.dto.PostmortemActionItemCreateRequest;
import io.aegisops.execution.dto.PostmortemActionItemResponse;
import io.aegisops.execution.dto.PostmortemActionItemStatusRequest;
import io.aegisops.execution.dto.PostmortemGenerateRequest;
import io.aegisops.execution.dto.PostmortemReportResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PostmortemController {
  private final PostmortemService service;

  public PostmortemController(PostmortemService service) {
    this.service = service;
  }

  @PostMapping("/api/incidents/{incidentId}/postmortems/generate")
  public ApiResponse<PostmortemReportResponse> generate(
      @PathVariable String incidentId,
      @RequestBody(required = false) PostmortemGenerateRequest request) {
    return ApiResponse.ok(service.generate(TenantContext.requireTenantId(), incidentId, request));
  }

  @GetMapping("/api/incidents/{incidentId}/postmortems/latest")
  public ApiResponse<PostmortemReportResponse> latest(@PathVariable String incidentId) {
    return ApiResponse.ok(service.latestByIncident(TenantContext.requireTenantId(), incidentId));
  }

  @GetMapping("/api/postmortems/{postmortemId}")
  public ApiResponse<PostmortemReportResponse> get(@PathVariable String postmortemId) {
    return ApiResponse.ok(service.get(TenantContext.requireTenantId(), postmortemId));
  }

  @GetMapping(value = "/api/postmortems/{postmortemId}/markdown", produces = "text/markdown")
  public String markdown(@PathVariable String postmortemId) {
    return service.markdown(TenantContext.requireTenantId(), postmortemId);
  }

  @PostMapping("/api/postmortems/{postmortemId}/action-items")
  public ApiResponse<PostmortemActionItemResponse> createActionItem(
      @PathVariable String postmortemId, @RequestBody PostmortemActionItemCreateRequest request) {
    return ApiResponse.ok(
        service.createActionItem(TenantContext.requireTenantId(), postmortemId, request));
  }

  @GetMapping("/api/postmortems/{postmortemId}/action-items")
  public ApiResponse<List<PostmortemActionItemResponse>> listActionItems(
      @PathVariable String postmortemId) {
    return ApiResponse.ok(service.listActionItems(TenantContext.requireTenantId(), postmortemId));
  }

  @PostMapping("/api/postmortem-action-items/{actionItemId}/status")
  public ApiResponse<PostmortemActionItemResponse> updateActionItemStatus(
      @PathVariable String actionItemId, @RequestBody PostmortemActionItemStatusRequest request) {
    return ApiResponse.ok(
        service.updateActionItemStatus(TenantContext.requireTenantId(), actionItemId, request));
  }
}
