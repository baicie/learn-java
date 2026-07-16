package io.aegisops.asset.api;

import io.aegisops.asset.api.dto.AssetImportPreviewResponse;
import io.aegisops.asset.api.dto.AssetImportRowPageResponse;
import io.aegisops.asset.api.dto.ResolveAssetImportRowRequest;
import io.aegisops.asset.application.AssetImportService;
import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/assets/imports")
public class AssetImportController {
  private final AssetImportService service;

  public AssetImportController(AssetImportService service) {
    this.service = service;
  }

  @GetMapping("/template")
  @PreAuthorize("hasAuthority('asset:import')")
  public ResponseEntity<byte[]> template() {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=asset-import-template.csv")
        .body(service.template());
  }

  @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAuthority('asset:import')")
  public ApiResponse<AssetImportPreviewResponse> preview(
      @RequestPart MultipartFile file, @RequestParam String sourceInstanceId, Principal principal)
      throws IOException {
    return ApiResponse.ok(
        service.preview(
            TenantContext.requireTenantId(),
            sourceInstanceId,
            file.getOriginalFilename(),
            file.getBytes(),
            principal.getName()));
  }

  @GetMapping("/{jobId}")
  @PreAuthorize("hasAuthority('asset:import')")
  public ApiResponse<AssetImportPreviewResponse> get(@PathVariable String jobId) {
    return ApiResponse.ok(service.get(TenantContext.requireTenantId(), jobId));
  }

  @GetMapping("/{jobId}/rows")
  @PreAuthorize("hasAuthority('asset:import')")
  public ApiResponse<AssetImportRowPageResponse> rows(
      @PathVariable String jobId,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "100") @Min(1) int pageSize,
      @RequestParam(required = false) String status) {
    return ApiResponse.ok(
        service.rows(TenantContext.requireTenantId(), jobId, page, pageSize, status));
  }

  @GetMapping("/{jobId}/problems.csv")
  @PreAuthorize("hasAuthority('asset:import')")
  public ResponseEntity<byte[]> exportProblems(
      @PathVariable String jobId, @RequestParam(required = false) String status) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=asset-import-" + jobId + "-problems.csv")
        .body(service.exportProblems(TenantContext.requireTenantId(), jobId, status));
  }

  @PostMapping("/{jobId}/rows/{rowNumber}/resolution")
  @PreAuthorize("hasAuthority('asset:import')")
  public ApiResponse<AssetImportPreviewResponse> resolveConflict(
      @PathVariable String jobId,
      @PathVariable @Min(1) int rowNumber,
      @Valid @RequestBody ResolveAssetImportRowRequest request,
      Principal principal) {
    return ApiResponse.ok(
        service.resolveConflict(
            TenantContext.requireTenantId(),
            jobId,
            rowNumber,
            request,
            principal.getName(),
            java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)));
  }

  @PostMapping("/{jobId}/confirm")
  @PreAuthorize("hasAuthority('asset:import')")
  public ApiResponse<AssetImportPreviewResponse> confirm(
      @PathVariable String jobId, Principal principal) {
    return ApiResponse.ok(
        service.confirm(TenantContext.requireTenantId(), jobId, principal.getName()));
  }

  @PostMapping("/{jobId}/cancel")
  @PreAuthorize("hasAuthority('asset:import')")
  public ApiResponse<Map<String, Boolean>> cancel(@PathVariable String jobId, Principal principal) {
    service.cancel(TenantContext.requireTenantId(), jobId, principal.getName());
    return ApiResponse.ok(Map.of("cancelled", true));
  }
}
