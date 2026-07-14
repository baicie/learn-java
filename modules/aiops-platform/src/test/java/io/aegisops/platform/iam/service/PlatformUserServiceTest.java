package io.aegisops.platform.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.platform.audit.PlatformAuditService;
import io.aegisops.platform.iam.domain.ChangeUserStatusCommand;
import io.aegisops.platform.iam.domain.CreatePlatformUserData;
import io.aegisops.platform.iam.domain.PlatformUser;
import io.aegisops.platform.iam.domain.PlatformUserStatus;
import io.aegisops.platform.iam.domain.ReplaceUserRolesCommand;
import io.aegisops.platform.iam.domain.UpdatePlatformUserData;
import io.aegisops.platform.iam.error.IamDomainException;
import io.aegisops.platform.iam.repository.PlatformRoleRepository;
import io.aegisops.platform.iam.repository.PlatformUserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PlatformUserServiceTest {
  private PlatformUserRepository users;
  private PlatformRoleRepository roles;
  private PasswordEncoder encoder;
  private PlatformUserService service;

  @BeforeEach
  void setUp() {
    users = mock(PlatformUserRepository.class);
    roles = mock(PlatformRoleRepository.class);
    encoder = mock(PasswordEncoder.class);
    service = new PlatformUserService(users, roles, encoder, mock(PlatformAuditService.class));
  }

  @Test
  void createPersistsUserAndRoles() {
    PlatformUser user = user(PlatformUserStatus.ACTIVE, List.of());
    when(encoder.encode("password1")).thenReturn("hash");
    when(roles.exists("operator")).thenReturn(true);
    when(users.insert(any())).thenReturn(Optional.of(user));
    when(users.findById("u1")).thenReturn(Optional.of(user));

    PlatformUser result =
        service.create(
            new CreatePlatformUserData(
                "alice", "Alice", "a@example.com", "password1", "active", Set.of("operator")),
            "admin");

    assertThat(result).isEqualTo(user);
    verify(users).replaceRoles(any(), any(), any(), any());
  }

  @Test
  void createRejectsDuplicateAndMissingRole() {
    when(users.existsByUsername(null, "alice")).thenReturn(true);
    assertThatThrownBy(
            () ->
                service.create(
                    new CreatePlatformUserData(
                        "alice", "Alice", null, "password1", "active", Set.of()),
                    "admin"))
        .isInstanceOf(IamDomainException.class);

    when(users.existsByUsername(null, "alice")).thenReturn(false);
    when(encoder.encode("password1")).thenReturn("hash");
    when(users.insert(any())).thenReturn(Optional.of(user(PlatformUserStatus.ACTIVE, List.of())));
    assertThatThrownBy(
            () ->
                service.create(
                    new CreatePlatformUserData(
                        "alice", "Alice", null, "password1", "active", Set.of("missing")),
                    "admin"))
        .isInstanceOf(IamDomainException.class);
  }

  @Test
  void updateStatusPasswordAndRolesEnforceRules() {
    PlatformUser user = user(PlatformUserStatus.ACTIVE, List.of());
    when(users.findById("u1")).thenReturn(Optional.of(user));
    when(users.updateStatus(any(), any(), any(), any(), any(Integer.class))).thenReturn(1);
    service.changeStatus("u1", new ChangeUserStatusCommand("locked", "risk", 1), "admin");

    assertThatThrownBy(() -> service.resetPassword("u1", "short", "admin"))
        .isInstanceOf(IamDomainException.class);
    when(encoder.encode("password1")).thenReturn("new-hash");
    service.resetPassword("u1", "password1", "admin");
    verify(users).updatePassword(eq("u1"), eq("new-hash"), any());

    when(roles.exists("operator")).thenReturn(true);
    service.replaceRoles("u1", new ReplaceUserRolesCommand(List.of("operator"), null, 1), "admin");
    service.update(
        "u1", new UpdatePlatformUserData("Alice 2", null, Set.of("operator"), Map.of()), "admin");
    verify(users).update(any(), any(), any(), any());
  }

  @Test
  void missingAndConflictingUsersFail() {
    when(users.findById("none")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.findById("none")).isInstanceOf(IamDomainException.class);

    when(users.findById("u1")).thenReturn(Optional.of(user(PlatformUserStatus.ACTIVE, List.of())));
    when(users.updateStatus(any(), any(), any(), any(), any(Integer.class))).thenReturn(0);
    assertThatThrownBy(
            () -> service.changeStatus("u1", new ChangeUserStatusCommand("disabled", null, 1), "a"))
        .isInstanceOf(IamDomainException.class);
  }

  @Test
  void administratorCannotDisableSelfOrRemoveOwnSystemAdminRole() {
    PlatformUser administrator =
        user(PlatformUserStatus.ACTIVE, List.of(new PlatformUser.RoleRef("system_admin", "系统管理员")));
    when(users.findById("u1")).thenReturn(Optional.of(administrator));

    assertThatThrownBy(
            () ->
                service.changeStatus(
                    "u1", new ChangeUserStatusCommand("disabled", "self", 1), "u1"))
        .isInstanceOf(IamDomainException.class);

    assertThatThrownBy(
            () ->
                service.replaceRoles(
                    "u1", new ReplaceUserRolesCommand(List.of("normal_user"), "self", 1), "u1"))
        .isInstanceOf(IamDomainException.class);
  }

  @Test
  void lastActiveSystemAdministratorCannotBeDisabled() {
    PlatformUser administrator =
        user(PlatformUserStatus.ACTIVE, List.of(new PlatformUser.RoleRef("system_admin", "系统管理员")));
    when(users.findById("u1")).thenReturn(Optional.of(administrator));
    when(users.countActiveUsersWithRole("system_admin")).thenReturn(1);

    assertThatThrownBy(
            () ->
                service.changeStatus(
                    "u1", new ChangeUserStatusCommand("disabled", "rotation", 1), "u2"))
        .isInstanceOf(IamDomainException.class);
    verify(users).lockRoleForUpdate("system_admin");
  }

  private PlatformUser user(PlatformUserStatus status, List<PlatformUser.RoleRef> roles) {
    OffsetDateTime now = OffsetDateTime.now();
    return new PlatformUser(
        "u1", "t1", "alice", "Alice", "a@example.com", status, roles, Map.of(), null, now, now, 1);
  }
}
