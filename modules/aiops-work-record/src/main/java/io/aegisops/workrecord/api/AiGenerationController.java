package io.aegisops.workrecord.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.service.AiGenerationService;
import io.aegisops.workrecord.domain.model.AiGeneration;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/ai-generations")
@ConditionalOnProperty(
    prefix = "aiops.runtime",
    name = "app",
    havingValue = "server",
    matchIfMissing = true)
public class AiGenerationController {
  private final AiGenerationService service;

  public AiGenerationController(AiGenerationService service) {
    this.service = service;
  }

  @PostMapping("/records/{recordId}/summary")
  @PreAuthorize("hasAuthority('work-record:ai:generate')")
  public ApiResponse<AiGeneration> summary(
      @PathVariable String recordId, @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(
        service.requestRecordSummary(TenantContext.requireTenantId(), recordId, principal));
  }

  @PostMapping("/monthly")
  @PreAuthorize("hasAuthority('work-record:ai:generate')")
  public ApiResponse<AiGeneration> monthly(
      @RequestParam LocalDate month, @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(
        service.requestMonthlyReport(TenantContext.requireTenantId(), month, principal));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('work-record:ai:generate')")
  public ApiResponse<List<AiGeneration>> list(
      @RequestParam String resourceType,
      @RequestParam String resourceId,
      @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(
        service.list(TenantContext.requireTenantId(), resourceType, resourceId, principal));
  }

  @PostMapping("/{id}/review")
  @PreAuthorize("hasAuthority('work-record:ai:review')")
  public ApiResponse<AiGeneration> review(
      @PathVariable String id,
      @RequestParam boolean accepted,
      @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(service.review(TenantContext.requireTenantId(), id, accepted, principal));
  }
}
