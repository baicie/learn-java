package io.aegisops.worker;

import io.aegisops.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/worker")
public class WorkerController {
    @GetMapping("/status")
    public ApiResponse<Map<String, String>> status() {
        return ApiResponse.ok(Map.of("status", "idle", "phase", "phase0"));
    }
}
