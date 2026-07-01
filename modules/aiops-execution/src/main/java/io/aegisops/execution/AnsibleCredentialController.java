package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.execution.dto.AnsibleCredentialCreateRequest;
import io.aegisops.execution.dto.AnsibleCredentialResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnsibleCredentialController {
  private final AnsibleResourceService service;

  public AnsibleCredentialController(AnsibleResourceService service) {
    this.service = service;
  }

  @GetMapping("/api/ansible-credentials")
  @PreAuthorize("hasAuthority('automation:read')")
  public ApiResponse<List<AnsibleCredentialResponse>> list(
      @RequestParam(defaultValue = "false") boolean includeDisabled) {
    return ApiResponse.ok(
        service.listCredentials(TenantContext.requireTenantId(), includeDisabled));
  }

  @PostMapping("/api/ansible-credentials")
  @PreAuthorize("hasAuthority('admin:manage')")
  public ApiResponse<AnsibleCredentialResponse> create(
      @RequestBody AnsibleCredentialCreateRequest request) {
    return ApiResponse.ok(service.createCredential(TenantContext.requireTenantId(), request));
  }

  @GetMapping("/api/ansible-credentials/{credentialId}")
  @PreAuthorize("hasAuthority('automation:read')")
  public ApiResponse<AnsibleCredentialResponse> get(@PathVariable String credentialId) {
    return ApiResponse.ok(service.getCredential(TenantContext.requireTenantId(), credentialId));
  }

  @PostMapping("/api/ansible-credentials/{credentialId}/enable")
  @PreAuthorize("hasAuthority('admin:manage')")
  public ApiResponse<AnsibleCredentialResponse> enable(@PathVariable String credentialId) {
    return ApiResponse.ok(
        service.setCredentialEnabled(TenantContext.requireTenantId(), credentialId, true));
  }

  @PostMapping("/api/ansible-credentials/{credentialId}/disable")
  @PreAuthorize("hasAuthority('admin:manage')")
  public ApiResponse<AnsibleCredentialResponse> disable(@PathVariable String credentialId) {
    return ApiResponse.ok(
        service.setCredentialEnabled(TenantContext.requireTenantId(), credentialId, false));
  }
}
