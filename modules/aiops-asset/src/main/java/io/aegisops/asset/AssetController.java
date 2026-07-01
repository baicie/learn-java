package io.aegisops.asset;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets")
public class AssetController {
  private final AssetQueryService service;

  public AssetController(AssetQueryService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('asset:read')")
  public ApiResponse<List<AssetRecord>> list() {
    return ApiResponse.ok(service.listRecent(TenantContext.requireTenantId()));
  }
}
