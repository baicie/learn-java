package io.aegisops.alert;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {
  private final AlertQueryService service;

  public AlertController(AlertQueryService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('alert:read')")
  public ApiResponse<List<AlertEventRecord>> list() {
    return ApiResponse.ok(service.listRecent(TenantContext.requireTenantId()));
  }
}
