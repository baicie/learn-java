package io.aegisops.server.platform;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.user.UserService;
import io.aegisops.user.UserSummary;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform")
public class PlatformUserController {
  private final UserService userService;

  public PlatformUserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/users")
  @PreAuthorize(
      "hasAnyAuthority("
          + "'work-record:read:self',"
          + "'work-record:read:all',"
          + "'work-record:write',"
          + "'admin:manage')")
  public ApiResponse<List<UserSummary>> listUsers() {
    String tenantId = TenantContext.requireTenantId();
    return ApiResponse.ok(
        userService.listByTenant(tenantId).stream().map(user -> UserSummary.from(user)).toList());
  }
}
