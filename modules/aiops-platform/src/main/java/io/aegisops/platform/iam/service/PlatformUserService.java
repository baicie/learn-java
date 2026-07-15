package io.aegisops.platform.iam.service;

import io.aegisops.platform.audit.PlatformAuditService;
import io.aegisops.platform.iam.domain.ChangeUserStatusCommand;
import io.aegisops.platform.iam.domain.CreatePlatformUserData;
import io.aegisops.platform.iam.domain.PlatformUser;
import io.aegisops.platform.iam.domain.PlatformUserPage;
import io.aegisops.platform.iam.domain.PlatformUserQuery;
import io.aegisops.platform.iam.domain.PlatformUserStatus;
import io.aegisops.platform.iam.domain.ReplaceUserRolesCommand;
import io.aegisops.platform.iam.domain.UpdatePlatformUserData;
import io.aegisops.platform.iam.error.IamDomainException;
import io.aegisops.platform.iam.error.IamErrorCode;
import io.aegisops.platform.iam.repository.PlatformRoleRepository;
import io.aegisops.platform.iam.repository.PlatformUserCreateCommand;
import io.aegisops.platform.iam.repository.PlatformUserRepository;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformUserService {

  private final PlatformUserRepository users;
  private final PlatformRoleRepository roles;
  private final PasswordEncoder passwordEncoder;
  private final PlatformAuditService audit;

  public PlatformUserService(
      PlatformUserRepository users,
      PlatformRoleRepository roles,
      PasswordEncoder passwordEncoder,
      PlatformAuditService audit) {
    this.users = users;
    this.roles = roles;
    this.passwordEncoder = passwordEncoder;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public PlatformUserPage list(PlatformUserQuery query) {
    return users.search(query.normalized());
  }

  @Transactional(readOnly = true)
  public PlatformUser findById(String id) {
    return users
        .findById(id)
        .orElseThrow(
            () -> new IamDomainException(IamErrorCode.USER_NOT_FOUND, "user " + id + " not found"));
  }

  @Transactional
  public PlatformUser create(CreatePlatformUserData data, String actor) {
    data.validate();
    if (users.existsByUsername(null, data.username())) {
      throw new IamDomainException(
          IamErrorCode.USERNAME_CONFLICT, "username " + data.username() + " already taken");
    }
    OffsetDateTime now = OffsetDateTime.now();
    String id = UUID.randomUUID().toString();
    PlatformUserStatus status = PlatformUserStatus.from(data.status());
    Optional<PlatformUser> created =
        users.insert(
            new PlatformUserCreateCommand(
                id,
                null,
                data.username(),
                data.displayName(),
                data.email(),
                passwordEncoder.encode(data.initialPassword()),
                status,
                now));
    PlatformUser persisted =
        created.orElseThrow(
            () ->
                new IamDomainException(
                    IamErrorCode.USER_NOT_FOUND, "user failed to persist: " + data.username()));
    if (data.roleCodes() != null && !data.roleCodes().isEmpty()) {
      validateRoles(data.roleCodes());
      users.replaceRoles(persisted.tenantId(), persisted.id(), List.copyOf(data.roleCodes()), now);
    }
    audit.recordChange(
        currentTenant(),
        actor,
        "platform.user.created",
        "platform.user",
        persisted.id(),
        Map.of(),
        Map.of("status", persisted.status().value()),
        Map.of("username", persisted.username()));
    return users
        .findById(persisted.id())
        .orElseThrow(
            () ->
                new IamDomainException(
                    IamErrorCode.USER_NOT_FOUND, "user vanished after create: " + persisted.id()));
  }

  @Transactional
  public PlatformUser update(String id, UpdatePlatformUserData data, String actor) {
    PlatformUser existing = findById(id);
    OffsetDateTime now = OffsetDateTime.now();
    users.update(id, data.displayName(), data.email(), now);
    if (data.roleCodes() != null) {
      validateRoles(data.roleCodes());
      users.replaceRoles(existing.tenantId(), id, List.copyOf(data.roleCodes()), now);
    }
    audit.recordChange(
        currentTenant(),
        actor,
        "platform.user.updated",
        "platform.user",
        id,
        Map.of(
            "displayName", existing.displayName(),
            "email", existing.email()),
        Map.of(
            "displayName", data.displayName() == null ? existing.displayName() : data.displayName(),
            "email", data.email() == null ? existing.email() : data.email()),
        Map.of("roles", data.roleCodes() == null ? "unchanged" : List.copyOf(data.roleCodes())));
    return findById(id);
  }

  @Transactional
  public void changeStatus(String id, ChangeUserStatusCommand command, String actor) {
    PlatformUser existing = findById(id);
    PlatformUserStatus nextStatus = PlatformUserStatus.from(command.status());
    if (!List.of(PlatformUserStatus.ACTIVE, PlatformUserStatus.DISABLED, PlatformUserStatus.LOCKED)
        .contains(nextStatus)) {
      throw new IamDomainException(
          IamErrorCode.VALIDATION_FAILED,
          "unsupported status transition target: " + command.status());
    }
    if (id.equals(actor) && nextStatus != PlatformUserStatus.ACTIVE) {
      throw new IamDomainException(
          IamErrorCode.SELF_STATUS_CHANGE_FORBIDDEN,
          "administrators cannot disable or lock their own account");
    }
    if (hasRole(existing, "system_admin") && nextStatus != PlatformUserStatus.ACTIVE) {
      users.lockRoleForUpdate("system_admin");
      if (users.countActiveUsersWithRole("system_admin") <= 1) {
        throw new IamDomainException(
            IamErrorCode.LAST_SYSTEM_ADMIN_REQUIRED,
            "the tenant must keep at least one active system administrator");
      }
    }
    int updated =
        users.updateStatus(
            id,
            nextStatus,
            nextStatus == PlatformUserStatus.LOCKED ? OffsetDateTime.now().plusMinutes(15) : null,
            OffsetDateTime.now(),
            command.rowVersion());
    if (updated == 0) {
      throw new IamDomainException(
          IamErrorCode.USER_VERSION_CONFLICT,
          "user " + id + " was modified concurrently, refresh and retry");
    }
    audit.recordChange(
        currentTenant(),
        actor,
        "platform.user.statusChanged",
        "platform.user",
        id,
        Map.of("status", existing.status().value()),
        Map.of("status", nextStatus.value()),
        Map.of("reason", command.reason() == null ? "" : command.reason()));
  }

  @Transactional
  public void resetPassword(String id, String newPassword, String actor) {
    PlatformUser existing = findById(id);
    if (newPassword == null || newPassword.length() < 8) {
      throw new IamDomainException(
          IamErrorCode.VALIDATION_FAILED, "password must be at least 8 characters");
    }
    users.updatePassword(id, passwordEncoder.encode(newPassword), OffsetDateTime.now());
    audit.recordChange(
        currentTenant(),
        actor,
        "platform.user.passwordReset",
        "platform.user",
        id,
        Map.of("passwordReset", false),
        Map.of("passwordReset", true),
        Map.of());
  }

  @Transactional
  public PlatformUser replaceRoles(String id, ReplaceUserRolesCommand command, String actor) {
    PlatformUser existing = findById(id);
    Set<String> safeRoles = new HashSet<>(command.roleCodes());
    if (id.equals(actor)
        && hasRole(existing, "system_admin")
        && !safeRoles.contains("system_admin")) {
      throw new IamDomainException(
          IamErrorCode.SELF_ROLE_REMOVAL_FORBIDDEN,
          "administrators cannot remove their own system administrator role");
    }
    validateRoles(safeRoles);
    users.replaceRoles(existing.tenantId(), id, List.copyOf(safeRoles), OffsetDateTime.now());
    audit.recordChange(
        currentTenant(),
        actor,
        "platform.user.rolesReplaced",
        "platform.user",
        id,
        Map.of("roles", existing.roles()),
        Map.of("roles", safeRoles),
        Map.of("reason", command.reason() == null ? "" : command.reason()));
    return findById(id);
  }

  private void validateRoles(Set<String> codes) {
    if (codes == null || codes.isEmpty()) {
      return;
    }
    for (String code : codes) {
      if (!roles.exists(code)) {
        throw new IamDomainException(
            IamErrorCode.ROLE_NOT_FOUND, "role " + code + " does not exist");
      }
    }
  }

  private static boolean hasRole(PlatformUser user, String code) {
    return user.roles().stream().anyMatch(role -> role.code().equals(code));
  }

  private static String currentTenant() {
    try {
      String tenant = io.aegisops.common.tenant.TenantContext.getTenantId();
      return tenant == null || tenant.isBlank() ? "default" : tenant;
    } catch (Throwable ignored) {
      return "default";
    }
  }
}
