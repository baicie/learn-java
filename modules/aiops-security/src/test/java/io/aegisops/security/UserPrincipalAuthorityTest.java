package io.aegisops.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class UserPrincipalAuthorityTest {
  @Test
  void adminRoleIncludesSecurityAuthoritiesAndPermissions() {
    UserPrincipal principal =
        new UserPrincipal("user_1", "tenant_1", "admin", "Admin", Set.of("admin"));

    Set<String> authorities =
        principal.getAuthorities().stream()
            .map(authority -> authority.getAuthority())
            .collect(java.util.stream.Collectors.toSet());

    assertTrue(authorities.contains("ROLE_admin"));
    assertTrue(authorities.contains("admin:manage"));
    assertTrue(authorities.contains("automation:approve"));
    assertTrue(authorities.contains("automation:execute"));
    assertTrue(authorities.contains("runbook:write"));
  }
}
