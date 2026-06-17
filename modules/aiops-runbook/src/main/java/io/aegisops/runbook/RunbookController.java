package io.aegisops.runbook;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.runbook.dto.CreateRunbookRequest;
import io.aegisops.runbook.dto.RunbookResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for runbook CRUD and enable/disable. */
@RestController
@RequestMapping("/api/runbooks")
public class RunbookController {
  private final AutomationPlanService service;

  public RunbookController(AutomationPlanService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<List<RunbookResponse>> list(
      @RequestParam(defaultValue = "false") boolean includeDisabled) {
    return ApiResponse.ok(service.listRunbooks(TenantContext.requireTenantId(), includeDisabled));
  }

  @PostMapping
  public ApiResponse<RunbookResponse> create(@RequestBody CreateRunbookRequest request) {
    return ApiResponse.ok(service.createRunbook(TenantContext.requireTenantId(), request));
  }

  @GetMapping("/{runbookId}")
  public ApiResponse<RunbookResponse> get(@PathVariable String runbookId) {
    return ApiResponse.ok(service.getRunbook(TenantContext.requireTenantId(), runbookId));
  }

  @PostMapping("/{runbookId}/enable")
  public ApiResponse<RunbookResponse> enable(@PathVariable String runbookId) {
    return ApiResponse.ok(
        service.setRunbookEnabled(TenantContext.requireTenantId(), runbookId, true));
  }

  @PostMapping("/{runbookId}/disable")
  public ApiResponse<RunbookResponse> disable(@PathVariable String runbookId) {
    return ApiResponse.ok(
        service.setRunbookEnabled(TenantContext.requireTenantId(), runbookId, false));
  }
}
