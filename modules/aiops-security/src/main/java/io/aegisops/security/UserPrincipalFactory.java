package io.aegisops.security;

import io.aegisops.user.UserAccount;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Component;

@Component
public class UserPrincipalFactory {
  private final AuthorizationService authorizationService;

  public UserPrincipalFactory(AuthorizationService authorizationService) {
    this.authorizationService = authorizationService;
  }

  public UserPrincipal create(UserAccount account) {
    if (account == null) {
      throw new IllegalArgumentException("user account is required");
    }

    if (!"active".equalsIgnoreCase(account.status())) {
      throw new DisabledException("user is disabled");
    }

    AuthorizationSnapshot snapshot = authorizationService.resolve(account.tenantId(), account.id());

    return new UserPrincipal(
        account.id(),
        account.tenantId(),
        account.username(),
        account.displayName(),
        snapshot.roles(),
        snapshot.permissions(),
        snapshot.dataScopes());
  }
}
