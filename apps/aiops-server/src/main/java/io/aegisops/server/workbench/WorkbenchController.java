package io.aegisops.server.workbench;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workbench")
public class WorkbenchController {
  private final WorkbenchSummaryService service;

  public WorkbenchController(WorkbenchSummaryService service) {
    this.service = service;
  }

  @GetMapping("/summary")
  public ApiResponse<WorkbenchSummary> summary() {
    return ApiResponse.ok(service.summary(TenantContext.requireTenantId()));
  }
}
