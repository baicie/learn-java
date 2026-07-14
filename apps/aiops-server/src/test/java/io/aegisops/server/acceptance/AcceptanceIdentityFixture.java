package io.aegisops.server.acceptance;

import io.aegisops.security.AuthorizationService;
import io.aegisops.tenant.Tenant;
import io.aegisops.tenant.TenantRepository;
import io.aegisops.user.UserAccount;
import io.aegisops.user.UserRepository;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;

final class AcceptanceIdentityFixture {

  static final String PASSWORD = "Acc3ptance!2026";

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final AuthorizationService authorizationService;
  private final PasswordEncoder passwordEncoder;

  AcceptanceIdentityFixture(
      TenantRepository tenantRepository,
      UserRepository userRepository,
      AuthorizationService authorizationService,
      PasswordEncoder passwordEncoder) {
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
    this.authorizationService = authorizationService;
    this.passwordEncoder = passwordEncoder;
  }

  Identities create() {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    Tenant tenant =
        tenantRepository.create("acceptance-" + suffix, "Phase 18 Acceptance " + suffix);

    Account admin = createAccount(tenant.id(), "acc-admin-" + suffix, "验收管理员", "system_admin");

    Account userA = createAccount(tenant.id(), "acc-user-a-" + suffix, "验收用户 A", "normal_user");

    Account userB = createAccount(tenant.id(), "acc-user-b-" + suffix, "验收用户 B", "normal_user");

    return new Identities(suffix, tenant.id(), admin, userA, userB);
  }

  private Account createAccount(
      String tenantId, String username, String displayName, String roleCode) {
    UserAccount user =
        userRepository.create(
            tenantId,
            username,
            displayName,
            username + "@example.test",
            passwordEncoder.encode(PASSWORD));

    authorizationService.assignRole(tenantId, user.id(), roleCode, "phase18-acceptance");

    return new Account(user.id(), user.username(), displayName, PASSWORD);
  }

  record Identities(String suffix, String tenantId, Account admin, Account userA, Account userB) {}

  record Account(String id, String username, String displayName, String password) {}
}
