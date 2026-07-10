package io.aegisops.user;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
  private final UserService service;

  public UserController(UserService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('admin:manage')")
  public ApiResponse<List<UserSummary>> list() {
    String tenantId = TenantContext.requireTenantId();
    return ApiResponse.ok(
        service.listByTenant(tenantId).stream().map(UserSummary::from).toList());
  }
}
