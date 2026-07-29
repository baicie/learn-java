package io.aegisops.integration.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.security.AuthenticatedActor;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.integration.api.dto.ZabbixWebhookTokenResponse;
import io.aegisops.integration.application.ZabbixWebhookTokenApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/datasources")
public class ZabbixWebhookTokenController {
  private final ZabbixWebhookTokenApplicationService service;

  public ZabbixWebhookTokenController(ZabbixWebhookTokenApplicationService service) {
    this.service = service;
  }

  @GetMapping("/{datasourceId}/zabbix-webhook-token")
  @PreAuthorize("hasAuthority('datasource:write')")
  public ResponseEntity<ApiResponse<ZabbixWebhookTokenResponse>> token(
      @PathVariable("datasourceId") String datasourceId,
      @AuthenticationPrincipal AuthenticatedActor principal,
      HttpServletRequest request) {
    String tenantId = TenantContext.requireTenantId();
    String token =
        service.issueToken(
            tenantId,
            datasourceId,
            principal.id(),
            MDC.get("requestId"),
            request.getRemoteAddr(),
            request.getHeader("User-Agent"));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.ok(new ZabbixWebhookTokenResponse(token)));
  }
}
