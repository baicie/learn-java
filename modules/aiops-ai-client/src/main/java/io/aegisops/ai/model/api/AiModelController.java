package io.aegisops.ai.model.api;

import io.aegisops.ai.model.ServerSideAiModelManagement;
import io.aegisops.ai.model.application.AiModelService;
import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ServerSideAiModelManagement
@RequestMapping("/api/ai/models")
@PreAuthorize("hasAuthority('admin:manage')")
public class AiModelController {
  private final AiModelService service;

  public AiModelController(AiModelService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<List<AiModelResponse>> list() {
    return ApiResponse.ok(service.list(TenantContext.requireTenantId()));
  }

  @PostMapping
  public ApiResponse<AiModelResponse> create(
      @Valid @RequestBody CreateAiModelRequest request, Principal principal) {
    return ApiResponse.ok(
        service.create(TenantContext.requireTenantId(), principal.getName(), request));
  }

  @PutMapping("/{id}")
  public ApiResponse<AiModelResponse> update(
      @PathVariable String id,
      @Valid @RequestBody UpdateAiModelRequest request,
      Principal principal) {
    return ApiResponse.ok(
        service.update(TenantContext.requireTenantId(), principal.getName(), id, request));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable String id, Principal principal) {
    service.delete(TenantContext.requireTenantId(), principal.getName(), id);
    return ApiResponse.ok(null);
  }

  @PostMapping("/{id}/default")
  public ApiResponse<AiModelResponse> setDefault(@PathVariable String id, Principal principal) {
    return ApiResponse.ok(
        service.setDefault(TenantContext.requireTenantId(), principal.getName(), id));
  }

  @PostMapping("/{id}/test")
  public ApiResponse<TestAiModelResponse> test(@PathVariable String id, Principal principal) {
    return ApiResponse.ok(
        service.testConnection(TenantContext.requireTenantId(), principal.getName(), id));
  }
}
