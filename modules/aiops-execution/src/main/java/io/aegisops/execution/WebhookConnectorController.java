package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.execution.dto.WebhookConnectorCreateRequest;
import io.aegisops.execution.dto.WebhookConnectorResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebhookConnectorController {
  private final WebhookConnectorService service;

  public WebhookConnectorController(WebhookConnectorService service) {
    this.service = service;
  }

  @GetMapping("/api/webhook-connectors")
  @PreAuthorize("hasAuthority('automation:read')")
  public ApiResponse<List<WebhookConnectorResponse>> list(
      @RequestParam(defaultValue = "false") boolean includeDisabled) {
    return ApiResponse.ok(service.list(TenantContext.requireTenantId(), includeDisabled));
  }

  @PostMapping("/api/webhook-connectors")
  @PreAuthorize("hasAuthority('automation:execute')")
  public ApiResponse<WebhookConnectorResponse> create(
      @RequestBody WebhookConnectorCreateRequest request) {
    return ApiResponse.ok(service.create(TenantContext.requireTenantId(), request));
  }

  @GetMapping("/api/webhook-connectors/{connectorId}")
  @PreAuthorize("hasAuthority('automation:read')")
  public ApiResponse<WebhookConnectorResponse> get(@PathVariable String connectorId) {
    return ApiResponse.ok(service.get(TenantContext.requireTenantId(), connectorId));
  }

  @PostMapping("/api/webhook-connectors/{connectorId}/enable")
  @PreAuthorize("hasAuthority('automation:execute')")
  public ApiResponse<WebhookConnectorResponse> enable(@PathVariable String connectorId) {
    return ApiResponse.ok(service.setEnabled(TenantContext.requireTenantId(), connectorId, true));
  }

  @PostMapping("/api/webhook-connectors/{connectorId}/disable")
  @PreAuthorize("hasAuthority('automation:execute')")
  public ApiResponse<WebhookConnectorResponse> disable(@PathVariable String connectorId) {
    return ApiResponse.ok(service.setEnabled(TenantContext.requireTenantId(), connectorId, false));
  }
}
