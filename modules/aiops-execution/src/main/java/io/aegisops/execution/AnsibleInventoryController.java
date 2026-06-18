package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.execution.dto.AnsibleInventoryCreateRequest;
import io.aegisops.execution.dto.AnsibleInventoryResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnsibleInventoryController {
  private final AnsibleResourceService service;

  public AnsibleInventoryController(AnsibleResourceService service) {
    this.service = service;
  }

  @GetMapping("/api/ansible-inventories")
  public ApiResponse<List<AnsibleInventoryResponse>> list(
      @RequestParam(defaultValue = "false") boolean includeDisabled) {
    return ApiResponse.ok(
        service.listInventories(TenantContext.requireTenantId(), includeDisabled));
  }

  @PostMapping("/api/ansible-inventories")
  public ApiResponse<AnsibleInventoryResponse> create(
      @RequestBody AnsibleInventoryCreateRequest request) {
    return ApiResponse.ok(service.createInventory(TenantContext.requireTenantId(), request));
  }

  @GetMapping("/api/ansible-inventories/{inventoryId}")
  public ApiResponse<AnsibleInventoryResponse> get(@PathVariable String inventoryId) {
    return ApiResponse.ok(service.getInventory(TenantContext.requireTenantId(), inventoryId));
  }

  @PostMapping("/api/ansible-inventories/{inventoryId}/enable")
  public ApiResponse<AnsibleInventoryResponse> enable(@PathVariable String inventoryId) {
    return ApiResponse.ok(
        service.setInventoryEnabled(TenantContext.requireTenantId(), inventoryId, true));
  }

  @PostMapping("/api/ansible-inventories/{inventoryId}/disable")
  public ApiResponse<AnsibleInventoryResponse> disable(@PathVariable String inventoryId) {
    return ApiResponse.ok(
        service.setInventoryEnabled(TenantContext.requireTenantId(), inventoryId, false));
  }
}
