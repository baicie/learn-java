package io.aegisops.platform;

import io.aegisops.common.api.ApiResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/modules")
public class PlatformModuleController {
  private final PlatformModuleService service;

  public PlatformModuleController(PlatformModuleService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('admin:manage')")
  public ApiResponse<List<PlatformModule>> list() {
    return ApiResponse.ok(service.listModules());
  }
}
