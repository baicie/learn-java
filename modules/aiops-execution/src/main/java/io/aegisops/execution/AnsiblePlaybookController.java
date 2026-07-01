package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.execution.dto.AnsiblePlaybookCreateRequest;
import io.aegisops.execution.dto.AnsiblePlaybookResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnsiblePlaybookController {
  private final AnsibleResourceService service;

  public AnsiblePlaybookController(AnsibleResourceService service) {
    this.service = service;
  }

  @GetMapping("/api/ansible-playbooks")
  @PreAuthorize("hasAuthority('runbook:read')")
  public ApiResponse<List<AnsiblePlaybookResponse>> list(
      @RequestParam(defaultValue = "false") boolean includeDisabled) {
    return ApiResponse.ok(service.listPlaybooks(TenantContext.requireTenantId(), includeDisabled));
  }

  @PostMapping("/api/ansible-playbooks")
  @PreAuthorize("hasAuthority('runbook:write')")
  public ApiResponse<AnsiblePlaybookResponse> create(
      @RequestBody AnsiblePlaybookCreateRequest request) {
    return ApiResponse.ok(service.createPlaybook(TenantContext.requireTenantId(), request));
  }

  @GetMapping("/api/ansible-playbooks/{playbookId}")
  @PreAuthorize("hasAuthority('runbook:read')")
  public ApiResponse<AnsiblePlaybookResponse> get(@PathVariable String playbookId) {
    return ApiResponse.ok(service.getPlaybook(TenantContext.requireTenantId(), playbookId));
  }

  @PostMapping("/api/ansible-playbooks/{playbookId}/enable")
  @PreAuthorize("hasAuthority('runbook:write')")
  public ApiResponse<AnsiblePlaybookResponse> enable(@PathVariable String playbookId) {
    return ApiResponse.ok(
        service.setPlaybookEnabled(TenantContext.requireTenantId(), playbookId, true));
  }

  @PostMapping("/api/ansible-playbooks/{playbookId}/disable")
  @PreAuthorize("hasAuthority('runbook:write')")
  public ApiResponse<AnsiblePlaybookResponse> disable(@PathVariable String playbookId) {
    return ApiResponse.ok(
        service.setPlaybookEnabled(TenantContext.requireTenantId(), playbookId, false));
  }
}
