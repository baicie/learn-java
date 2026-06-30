package io.aegisops.worker;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.runtime.RuntimeProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Worker internal status endpoint.
 *
 * <p>Returns the current runtime MVP phase as configured in {@code application.yml}. Phase
 * discovery tests live next to the enum and properties class; controller-level wiring is
 * exercised by integration smoke tests of the {@code /internal/worker/status} route.
 */
@RestController
@RequestMapping("/internal/worker")
public class WorkerController {
  private final RuntimeProperties runtimeProperties;

  public WorkerController(RuntimeProperties runtimeProperties) {
    this.runtimeProperties = runtimeProperties;
  }

  @GetMapping("/status")
  public ApiResponse<Map<String, String>> status() {
    Map<String, String> body = new LinkedHashMap<>();
    body.put("status", "idle");
    body.put("phase", runtimeProperties.phase().propertyName());
    return ApiResponse.ok(body);
  }
}
