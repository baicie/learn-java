package io.aegisops.plugin;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.plugin.dto.PluginDescriptorResponse;
import io.aegisops.plugin.dto.PluginDisableRequest;
import io.aegisops.plugin.dto.PluginEnableRequest;
import io.aegisops.plugin.dto.PluginExtensionPointResponse;
import io.aegisops.plugin.dto.TenantFrontendManifestResponse;
import io.aegisops.plugin.dto.TenantPluginResponse;
import io.aegisops.plugin.dto.TenantPluginToolPolicyResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class PluginController {
  private final PluginService service;

  public PluginController(PluginService service) {
    this.service = service;
  }

  @GetMapping("/api/plugin-extension-points")
  public ApiResponse<List<PluginExtensionPointResponse>> extensionPoints() {
    return ApiResponse.ok(service.listExtensionPoints());
  }

  @GetMapping("/api/plugins")
  public ApiResponse<List<PluginDescriptorResponse>> listPlugins() {
    return ApiResponse.ok(service.listPlugins());
  }

  @GetMapping("/api/plugins/{pluginId}")
  public ApiResponse<PluginDescriptorResponse> getPlugin(@PathVariable String pluginId) {
    return ApiResponse.ok(service.getPlugin(pluginId));
  }

  @GetMapping("/api/tenant/plugins")
  public ApiResponse<List<TenantPluginResponse>> listTenantPlugins() {
    return ApiResponse.ok(service.listTenantPlugins(TenantContext.requireTenantId()));
  }

  @PostMapping("/api/plugins/{pluginId}/enable")
  public ApiResponse<TenantPluginResponse> enable(
      @PathVariable String pluginId, @RequestBody(required = false) PluginEnableRequest request) {
    return ApiResponse.ok(service.enablePlugin(TenantContext.requireTenantId(), pluginId, request));
  }

  @PostMapping("/api/tenant/plugins/{tenantPluginId}/disable")
  public ApiResponse<TenantPluginResponse> disable(
      @PathVariable String tenantPluginId,
      @RequestBody(required = false) PluginDisableRequest request) {
    return ApiResponse.ok(
        service.disablePlugin(TenantContext.requireTenantId(), tenantPluginId, request));
  }

  @GetMapping("/api/tenant/plugins/frontend-manifest")
  public ApiResponse<TenantFrontendManifestResponse> frontendManifest() {
    return ApiResponse.ok(service.frontendManifest(TenantContext.requireTenantId()));
  }

  @GetMapping("/api/tenant/plugins/tools")
  public ApiResponse<List<TenantPluginToolPolicyResponse>> toolPolicies() {
    return ApiResponse.ok(service.listToolPolicies(TenantContext.requireTenantId()));
  }

  @PostMapping("/api/tenant/plugins/{tenantPluginId}/tools/{toolKey}/allow")
  public ApiResponse<TenantPluginToolPolicyResponse> allowTool(
      @PathVariable String tenantPluginId,
      @PathVariable String toolKey,
      @RequestParam(defaultValue = "system") String actor) {
    return ApiResponse.ok(
        service.allowTool(TenantContext.requireTenantId(), tenantPluginId, toolKey, actor));
  }

  @PostMapping("/api/tenant/plugins/{tenantPluginId}/tools/{toolKey}/deny")
  public ApiResponse<TenantPluginToolPolicyResponse> denyTool(
      @PathVariable String tenantPluginId,
      @PathVariable String toolKey,
      @RequestParam(defaultValue = "system") String actor) {
    return ApiResponse.ok(
        service.denyTool(TenantContext.requireTenantId(), tenantPluginId, toolKey, actor));
  }
}
