package io.aegisops.datasource;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.datasource.api.dto.StartSyncResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/datasources")
public class DataSourceController {
  private final DataSourceService service;

  public DataSourceController(DataSourceService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('datasource:read')")
  public ApiResponse<List<DataSourceRecord>> list() {
    String tenantId = TenantContext.requireTenantId();
    return ApiResponse.ok(service.list(tenantId));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('datasource:write')")
  public ApiResponse<DataSourceRecord> create(@Valid @RequestBody CreateDataSourceRequest request) {
    String tenantId = TenantContext.requireTenantId();
    return ApiResponse.ok(service.create(tenantId, request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('datasource:write')")
  public ApiResponse<DataSourceRecord> update(
      @PathVariable("id") String id, @Valid @RequestBody UpdateDataSourceRequest request) {
    String tenantId = TenantContext.requireTenantId();
    return ApiResponse.ok(service.update(tenantId, id, request));
  }

  @PostMapping("/{id}/test")
  @PreAuthorize("hasAuthority('datasource:write')")
  public ApiResponse<TestDataSourceResponse> test(@PathVariable("id") String id) {
    String tenantId = TenantContext.requireTenantId();
    return ApiResponse.ok(service.test(tenantId, id));
  }

  @PostMapping("/{id}/sync")
  @PreAuthorize("hasAuthority('datasource:write')")
  public ResponseEntity<ApiResponse<StartSyncResponse>> sync(@PathVariable("id") String id) {
    String tenantId = TenantContext.requireTenantId();
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(ApiResponse.ok(service.startSync(tenantId, id)));
  }

  @GetMapping("/{id}/sync-runs")
  @PreAuthorize("hasAuthority('datasource:read')")
  public ApiResponse<List<SyncRunRecord>> syncRuns(@PathVariable("id") String id) {
    String tenantId = TenantContext.requireTenantId();
    return ApiResponse.ok(service.syncRuns(tenantId, id));
  }
}
