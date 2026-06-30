package io.aegisops.runner;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.runtime.RuntimeProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/runner")
public class RunnerController {
  private final RuntimeProperties runtimeProperties;

  public RunnerController(RuntimeProperties runtimeProperties) {
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
