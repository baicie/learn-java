package io.aegisops.workrecord.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.service.FieldPolicyService;
import io.aegisops.workrecord.domain.model.FieldPolicy;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/template-versions/{versionId}/field-policies")
@ConditionalOnProperty(
    prefix = "aiops.runtime",
    name = "app",
    havingValue = "app",
    matchIfMissing = true)
public class WorkRecordFieldPolicyController {
  private final FieldPolicyService service;

  public WorkRecordFieldPolicyController(FieldPolicyService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('work-record:template:write')")
  public ApiResponse<List<FieldPolicy>> list(
      @PathVariable String versionId, @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(service.list(TenantContext.requireTenantId(), versionId, principal));
  }

  @PutMapping
  @PreAuthorize("hasAuthority('work-record:template:write')")
  public ApiResponse<Void> replace(
      @PathVariable String versionId,
      @RequestBody List<FieldPolicyRequest> requests,
      @AuthenticationPrincipal UserPrincipal principal) {
    List<FieldPolicy> policies =
        requests == null
            ? List.of()
            : requests.stream().map(request -> request.toPolicy(versionId)).toList();
    service.replace(TenantContext.requireTenantId(), versionId, policies, principal);
    return ApiResponse.ok(null);
  }

  public record FieldPolicyRequest(
      String fieldCode,
      List<String> readRoles,
      List<String> writeRoles,
      FieldPolicy.MaskMode maskMode) {
    FieldPolicy toPolicy(String versionId) {
      return new FieldPolicy(versionId, fieldCode, readRoles, writeRoles, maskMode);
    }
  }
}
