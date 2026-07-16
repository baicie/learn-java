package io.aegisops.asset.api;

import io.aegisops.asset.api.dto.AssetIdentityResponse;
import io.aegisops.asset.api.dto.AssetPageResponse;
import io.aegisops.asset.api.dto.AssetRelationResponse;
import io.aegisops.asset.api.dto.AssetResponse;
import io.aegisops.asset.api.dto.AssetSourceResponse;
import io.aegisops.asset.api.dto.CreateAssetRelationRequest;
import io.aegisops.asset.api.dto.CreateAssetRequest;
import io.aegisops.asset.api.dto.UpdateAssetRequest;
import io.aegisops.asset.application.AssetManagementService;
import io.aegisops.asset.application.AssetQuery;
import io.aegisops.asset.application.AssetQueryService;
import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/assets")
public class AssetController {
  private final AssetQueryService queryService;
  private final AssetManagementService managementService;

  public AssetController(AssetQueryService queryService, AssetManagementService managementService) {
    this.queryService = queryService;
    this.managementService = managementService;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('asset:read')")
  public ApiResponse<AssetPageResponse> list(
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) int pageSize,
      @RequestParam(required = false) String assetType,
      @RequestParam(required = false) String sourceType,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status) {
    int boundedPageSize = Math.min(pageSize, 100);
    return ApiResponse.ok(
        queryService.page(
            TenantContext.requireTenantId(),
            new AssetQuery(page, boundedPageSize, assetType, sourceType, keyword, status)));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('asset:write')")
  public ApiResponse<AssetResponse> create(
      @Valid @RequestBody CreateAssetRequest request, Principal principal) {
    return ApiResponse.ok(
        managementService.create(TenantContext.requireTenantId(), request, principal.getName()));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('asset:read')")
  public ApiResponse<AssetResponse> get(@PathVariable String id) {
    return ApiResponse.ok(queryService.get(TenantContext.requireTenantId(), id));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('asset:write')")
  public ApiResponse<AssetResponse> update(
      @PathVariable String id,
      @Valid @RequestBody UpdateAssetRequest request,
      Principal principal) {
    return ApiResponse.ok(
        managementService.update(
            TenantContext.requireTenantId(), id, request, principal.getName()));
  }

  @PostMapping("/{id}/archive")
  @PreAuthorize("hasAuthority('asset:write')")
  public ApiResponse<Map<String, Boolean>> archive(
      @PathVariable String id, @RequestParam @Min(0) long version, Principal principal) {
    managementService.archive(TenantContext.requireTenantId(), id, version, principal.getName());
    return ApiResponse.ok(Map.of("archived", true));
  }

  @GetMapping("/{id}/sources")
  @PreAuthorize("hasAuthority('asset:read')")
  public ApiResponse<List<AssetSourceResponse>> sources(@PathVariable String id) {
    return ApiResponse.ok(queryService.sources(TenantContext.requireTenantId(), id));
  }

  @GetMapping("/{id}/identities")
  @PreAuthorize("hasAuthority('asset:read')")
  public ApiResponse<List<AssetIdentityResponse>> identities(@PathVariable String id) {
    return ApiResponse.ok(queryService.identities(TenantContext.requireTenantId(), id));
  }

  @GetMapping("/{id}/relations")
  @PreAuthorize("hasAuthority('asset:read')")
  public ApiResponse<List<AssetRelationResponse>> relations(@PathVariable String id) {
    return ApiResponse.ok(queryService.relations(TenantContext.requireTenantId(), id));
  }

  @PostMapping("/{id}/relations")
  @PreAuthorize("hasAuthority('asset:write')")
  public ApiResponse<Map<String, String>> createRelation(
      @PathVariable String id,
      @Valid @RequestBody CreateAssetRelationRequest request,
      Principal principal) {
    String relationId =
        managementService.createRelation(
            TenantContext.requireTenantId(), id, request, principal.getName());
    return ApiResponse.ok(Map.of("relationId", relationId));
  }

  @DeleteMapping("/{id}/relations/{relationId}")
  @PreAuthorize("hasAuthority('asset:write')")
  public ApiResponse<Map<String, Boolean>> deleteRelation(
      @PathVariable String id, @PathVariable String relationId, Principal principal) {
    managementService.deleteRelation(
        TenantContext.requireTenantId(), id, relationId, principal.getName());
    return ApiResponse.ok(Map.of("deleted", true));
  }
}
