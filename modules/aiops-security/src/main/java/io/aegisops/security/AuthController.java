package io.aegisops.security;

import io.aegisops.audit.AuditService;
import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.exception.AppException;
import io.aegisops.user.UserAccount;
import io.aegisops.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final JwtTokenService tokenService;
    private final AuditService auditService;

    public AuthController(UserService userService, JwtTokenService tokenService, AuditService auditService) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        UserAccount user = userService.findByUsername(request.username())
                .filter(candidate -> userService.passwordMatches(request.password(), candidate))
                .filter(candidate -> "active".equals(candidate.status()))
                .orElseThrow(() -> new AppException("INVALID_CREDENTIALS", "Invalid username or password"));
        UserPrincipal principal = new UserPrincipal(user.id(), user.tenantId(), user.username(), user.displayName(), user.roles());
        String token = tokenService.issue(principal);
        auditService.record(user.tenantId(), user.id(), "auth.login", "user", user.id(), "{}");
        return ApiResponse.ok(new LoginResponse(token, new MeResponse(user.id(), user.tenantId(), user.username(), user.displayName(), user.roles())));
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(new MeResponse(principal.id(), principal.tenantId(), principal.username(), principal.displayName(), principal.roles()));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record LoginResponse(String token, MeResponse user) {}
    public record MeResponse(String id, String tenantId, String username, String displayName, Set<String> roles) {}
}
