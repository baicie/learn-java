package io.aegisops.workrecord.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.api.PageResult;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.AsyncJobQuery;
import io.aegisops.workrecord.application.service.AsyncJobDownloadService;
import io.aegisops.workrecord.application.service.AsyncJobService;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/async-jobs")
public class AsyncJobController {
  private final AsyncJobService service;
  private final AsyncJobDownloadService downloads;

  public AsyncJobController(AsyncJobService service, AsyncJobDownloadService downloads) {
    this.service = service;
    this.downloads = downloads;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('work-record:read:all') or hasAuthority('work-record:read:self')")
  public ApiResponse<PageResult<AsyncJobResponse>> page(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String jobType,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @AuthenticationPrincipal UserPrincipal user) {
    PageResult<io.aegisops.workrecord.domain.model.AsyncJob> result =
        service.page(
            TenantContext.requireTenantId(),
            new AsyncJobQuery(
                user.id(),
                user.hasPermission("work-record:read:all"),
                status,
                jobType,
                page,
                size));
    List<AsyncJobResponse> items = result.items().stream().map(AsyncJobResponse::from).toList();
    return ApiResponse.ok(new PageResult<>(result.total(), result.page(), result.size(), items));
  }

  @GetMapping("/{jobId}")
  @PreAuthorize("hasAuthority('work-record:read:all') or hasAuthority('work-record:read:self')")
  public ApiResponse<AsyncJobResponse> get(
      @PathVariable String jobId, @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        AsyncJobResponse.from(
            service.get(
                TenantContext.requireTenantId(),
                jobId,
                user.id(),
                user.hasPermission("work-record:read:all"))));
  }

  @PostMapping("/{jobId}/cancel")
  @PreAuthorize("hasAuthority('work-record:read:all') or hasAuthority('work-record:read:self')")
  public ApiResponse<AsyncJobResponse> cancel(
      @PathVariable String jobId, @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        AsyncJobResponse.from(
            service.cancel(
                TenantContext.requireTenantId(),
                jobId,
                user.id(),
                user.hasPermission("work-record:read:all"))));
  }

  @PostMapping("/{jobId}/download")
  @PreAuthorize("hasAuthority('work-record:read:all') or hasAuthority('work-record:read:self')")
  public ResponseEntity<ApiResponse<AsyncJobDownloadService.Download>> download(
      @PathVariable String jobId, @AuthenticationPrincipal UserPrincipal user) {
    var result = downloads.download(TenantContext.requireTenantId(), jobId, user);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(result));
  }
}
