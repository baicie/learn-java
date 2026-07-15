package io.aegisops.workrecord.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.TemplateMarketRepository;
import io.aegisops.workrecord.application.service.TemplateMarketService;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/template-market")
public class TemplateMarketController {
  private final TemplateMarketService service;

  public TemplateMarketController(TemplateMarketService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<List<TemplateMarketRepository.MarketVersion>> list() {
    return ApiResponse.ok(service.list(TenantContext.requireTenantId()));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('work-record:market:publish')")
  public ApiResponse<TemplateMarketRepository.MarketVersion> publish(
      @RequestBody PublishRequest request, @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(
        service.publish(
            TenantContext.requireTenantId(),
            new TemplateMarketService.PublishPackage(
                request.templateId(),
                request.packageCode(),
                request.name(),
                request.category(),
                request.visibility()),
            principal));
  }

  @PostMapping("/{versionId}/install")
  @PreAuthorize("hasAuthority('work-record:market:install')")
  public ApiResponse<WorkRecordTemplate> install(
      @PathVariable String versionId,
      @RequestBody InstallRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(
        service.install(
            TenantContext.requireTenantId(), versionId, request.targetTemplateCode(), principal));
  }

  public record PublishRequest(
      String templateId, String packageCode, String name, String category, String visibility) {}

  public record InstallRequest(String targetTemplateCode) {}
}
