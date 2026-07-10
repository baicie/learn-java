package io.aegisops.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserPrincipalAuthorityTest {

  @Test
  void shouldExposeDatabaseRolesAndPermissionsAsAuthorities() {
    UserPrincipal principal =
        new UserPrincipal(
            "u1",
            "t1",
            "alice",
            "Alice",
            Set.of(BuiltInRoleCodes.NORMAL_USER),
            Set.of(PermissionCodes.WORK_RECORD_READ_SELF, PermissionCodes.WORK_RECORD_WRITE),
            Map.of("work-record", DataScope.SELF));

    assertThat(principal.getAuthorities())
        .extracting(authority -> authority.getAuthority())
        .containsExactlyInAnyOrder(
            "ROLE_NORMAL_USER", "work-record:read:self", "work-record:write");
  }

  @Test
  void roleMustNotImplicitlyGrantHardcodedPermissions() {
    UserPrincipal principal =
        new UserPrincipal(
            "u1",
            "t1",
            "alice",
            "Alice",
            Set.of(BuiltInRoleCodes.SYSTEM_ADMIN),
            Set.of(),
            Map.of());

    assertThat(principal.hasPermission(PermissionCodes.WORK_RECORD_EXPORT)).isFalse();
  }
}
