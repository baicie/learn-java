package io.aegisops.server;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.runtime.RuntimeProperties;
import io.aegisops.common.tenant.TenantContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {
  private final SystemOverviewService overviewService;
  private final String appName;
  private final RuntimeProperties runtimeProperties;

  public SystemController(
      SystemOverviewService overviewService,
      RuntimeProperties runtimeProperties,
      @Value("${spring.application.name}") String appName) {
    this.overviewService = overviewService;
    this.runtimeProperties = runtimeProperties;
    this.appName = appName;
  }

  /**
   * Reports the runtime MVP phase and the app name. Unauthenticated by design so that health probes
   * and external monitors can identify the running build without a tenant context.
   */
  @GetMapping("/status")
  public ApiResponse<Map<String, String>> status() {
    Map<String, String> body = new LinkedHashMap<>();
    body.put("app", appName);
    body.put("phase", runtimeProperties.phase().propertyName());
    return ApiResponse.ok(body);
  }

  @GetMapping("/overview")
  public ApiResponse<Map<String, Object>> overview() {
    return ApiResponse.ok(overviewService.overview(TenantContext.requireTenantId()));
  }
}
