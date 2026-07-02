package io.aegisops.alert;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for standard alert ingest via webhook. */
@RestController
@RequestMapping("/api/alerts/ingest")
public class AlertIngestController {
  private final AlertIngestService service;

  public AlertIngestController(AlertIngestService service) {
    this.service = service;
  }

  @PostMapping("/webhook")
  @PreAuthorize("hasAuthority('alert:write')")
  public ApiResponse<AlertIngestResult> ingest(@RequestBody AlertIngestRequest request) {
    return ApiResponse.ok(service.ingest(TenantContext.requireTenantId(), request));
  }
}
