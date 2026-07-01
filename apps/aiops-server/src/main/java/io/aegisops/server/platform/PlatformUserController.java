package io.aegisops.server.platform;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.user.UserAccount;
import io.aegisops.user.UserService;
import java.util.List;
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
  public ApiResponse<List<UserAccount>> listUsers() {
    String tenantId = TenantContext.requireTenantId();
    return ApiResponse.ok(
        userService.listByTenant(tenantId).stream().map(this::hidePassword).toList());
  }

  private UserAccount hidePassword(UserAccount user) {
    return new UserAccount(
        user.id(),
        user.tenantId(),
        user.username(),
        user.displayName(),
        user.email(),
        null,
        user.status(),
        user.roles(),
        user.createdAt(),
        user.updatedAt());
  }
}
