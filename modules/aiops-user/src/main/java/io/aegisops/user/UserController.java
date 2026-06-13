package io.aegisops.user;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<UserAccount>> list() {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(service.listByTenant(tenantId).stream().map(this::safe).toList());
    }

    private UserAccount safe(UserAccount user) {
        return new UserAccount(user.id(), user.tenantId(), user.username(), user.displayName(), user.email(), null,
                user.status(), user.roles(), user.createdAt(), user.updatedAt());
    }
}
