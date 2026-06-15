package io.aegisops.runner;

import io.aegisops.common.api.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/runner")
public class RunnerController {
  @GetMapping("/status")
  public ApiResponse<Map<String, String>> status() {
    return ApiResponse.ok(Map.of("status", "idle", "phase", "phase0"));
  }
}
