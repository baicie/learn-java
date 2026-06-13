package io.aegisops.datasource;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/api/datasources")
public class DataSourceController {
    private final DataSourceService service;
    private final Executor syncExecutor;
    private final int syncTimeoutSeconds;

    public DataSourceController(DataSourceService service,
                                @Qualifier(DatasourceSyncExecutorConfig.SYNC_EXECUTOR) Executor syncExecutor) {
        this.service = service;
        this.syncExecutor = syncExecutor;
        this.syncTimeoutSeconds = DatasourceSyncExecutorConfig.SYNC_TIMEOUT_SECONDS;
    }

    @GetMapping
    public ApiResponse<List<DataSourceRecord>> list() {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(service.list(tenantId));
    }

    @PostMapping
    public ApiResponse<DataSourceRecord> create(@Valid @RequestBody CreateDataSourceRequest request) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(service.create(tenantId, request));
    }

    @PostMapping("/{id}/test")
    public ApiResponse<TestDataSourceResponse> test(@PathVariable("id") String id) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(service.test(tenantId, id));
    }

    @PostMapping("/{id}/sync")
    public ApiResponse<SyncDataSourceResponse> sync(@PathVariable("id") String id) {
        String tenantId = TenantContext.requireTenantId();
        try {
            CompletableFuture<SyncDataSourceResponse> future = CompletableFuture.supplyAsync(
                    () -> service.sync(tenantId, id), syncExecutor);
            return ApiResponse.ok(future.get(syncTimeoutSeconds, TimeUnit.SECONDS));
        } catch (TimeoutException ex) {
            throw new io.aegisops.common.exception.AppException(
                    "DATASOURCE_SYNC_TIMEOUT",
                    "Sync exceeded " + syncTimeoutSeconds + "s; check sync-runs for the latest status");
        } catch (java.util.concurrent.ExecutionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof io.aegisops.common.exception.AppException appEx) {
                throw appEx;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new io.aegisops.common.exception.AppException(
                    "DATASOURCE_SYNC_FAILED", cause.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new io.aegisops.common.exception.AppException(
                    "DATASOURCE_SYNC_INTERRUPTED", "Sync was interrupted");
        }
    }

    @GetMapping("/{id}/sync-runs")
    public ApiResponse<List<SyncRunRecord>> syncRuns(@PathVariable("id") String id) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(service.syncRuns(tenantId, id));
    }
}
