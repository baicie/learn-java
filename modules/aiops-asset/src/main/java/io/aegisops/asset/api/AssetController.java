package io.aegisops.asset.api;

import io.aegisops.asset.application.AssetQueryService;
import io.aegisops.asset.domain.model.Asset;
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
  public ApiResponse<List<Asset>> list() {
    return ApiResponse.ok(service.listRecent(TenantContext.requireTenantId()));
  }
}
