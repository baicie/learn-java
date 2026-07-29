package io.aegisops.integration.zabbix;

import io.aegisops.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/zabbix")
public class ZabbixWebhookController {
  private final ZabbixWebhookService service;

  public ZabbixWebhookController(ZabbixWebhookService service) {
    this.service = service;
  }

  @GetMapping("/health")
  public ApiResponse<String> health() {
    return ApiResponse.ok("zabbix integration is ready");
  }

  @PostMapping("/events")
  public ApiResponse<ZabbixWebhookIngestResponse> events(
      @RequestParam(value = "datasourceId", required = false) String datasourceId,
      @RequestHeader(value = "X-AegisOps-Webhook-Token", required = false) String token,
      @RequestBody ZabbixWebhookPayload payload) {
    return ApiResponse.ok(service.ingest(datasourceId, token, payload));
  }
}
