package io.aegisops.security;

import io.aegisops.audit.AuditService;
import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.exception.AppException;
import io.aegisops.security.api.CurrentAuthorizationResponse;
import io.aegisops.user.UserAccount;
import io.aegisops.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final UserService userService;
  private final JwtTokenService tokenService;
  private final AuditService auditService;
  private final UserPrincipalFactory principalFactory;

  public AuthController(
      UserService userService,
      JwtTokenService tokenService,
      AuditService auditService,
      UserPrincipalFactory principalFactory) {
    this.userService = userService;
    this.tokenService = tokenService;
    this.auditService = auditService;
    this.principalFactory = principalFactory;
  }

  @PostMapping("/login")
  public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    UserAccount user =
        userService
            .findByUsername(request.username())
            .filter(candidate -> userService.passwordMatches(request.password(), candidate))
            .filter(candidate -> "active".equals(candidate.status()))
            .orElseThrow(
                () -> new AppException("INVALID_CREDENTIALS", "Invalid username or password"));
    UserPrincipal principal = principalFactory.create(user);
    String token = tokenService.issue(principal);
    auditService.record(
        new io.aegisops.audit.AuditRecordCommand(
            user.tenantId(), user.id(), "auth.login", "user", user.id(), "{}"));
    Set<String> roles = principal.roles();
    return ApiResponse.ok(
        new LoginResponse(
            token,
            new MeResponse(
                principal.id(),
                principal.tenantId(),
                principal.getUsername(),
                principal.displayName(),
                roles)));
  }

  @GetMapping("/me")
  public ApiResponse<CurrentAuthorizationResponse> me(
      @AuthenticationPrincipal UserPrincipal principal) {
    if (principal == null) {
      throw new SecurityException("authentication is required");
    }
    return ApiResponse.ok(CurrentAuthorizationResponse.from(principal));
  }

  public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

  public record LoginResponse(String token, MeResponse user) {}

  public record MeResponse(
      String id, String tenantId, String username, String displayName, Set<String> roles) {}
}
