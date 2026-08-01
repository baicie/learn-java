package io.aegisops.workrecord.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.StatisticsQuery;
import io.aegisops.workrecord.application.command.StatisticsResult;
import io.aegisops.workrecord.application.command.WorkloadSummary;
import io.aegisops.workrecord.application.service.WorkRecordStatisticsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/analytics")
@ConditionalOnProperty(
    prefix = "aiops.runtime",
    name = "app",
    havingValue = "app",
    matchIfMissing = true)
public class WorkRecordAnalyticsController {
  private final WorkRecordStatisticsService service;

  public WorkRecordAnalyticsController(WorkRecordStatisticsService service) {
    this.service = service;
  }

  @GetMapping("/statistics")
  @PreAuthorize("hasAuthority('work-record:analytics')")
  public ApiResponse<StatisticsResult> statistics(
      @ModelAttribute StatisticsQuery query, @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(service.statistics(TenantContext.requireTenantId(), query, principal));
  }

  @GetMapping("/workload")
  @PreAuthorize("hasAuthority('work-record:analytics')")
  public ApiResponse<WorkloadSummary> workload(
      @ModelAttribute StatisticsQuery query, @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(service.workload(TenantContext.requireTenantId(), query, principal));
  }
}
