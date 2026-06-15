package io.aegisops.tenant;

import io.aegisops.common.api.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {
  private final TenantService service;

  public TenantController(TenantService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<List<Tenant>> list() {
    return ApiResponse.ok(service.list());
  }
}
