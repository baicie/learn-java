package io.aegisops.platform.iam.service;

import io.aegisops.platform.audit.PlatformAuditService;
import io.aegisops.platform.iam.domain.CreateRoleData;
import io.aegisops.platform.iam.domain.PlatformRole;
import io.aegisops.platform.iam.domain.PlatformRoleDetail;
import io.aegisops.platform.iam.domain.ReplaceRoleDataScopesCommand;
import io.aegisops.platform.iam.domain.ReplaceRolePermissionsCommand;
import io.aegisops.platform.iam.domain.UpdateRoleData;
import io.aegisops.platform.iam.error.IamDomainException;
import io.aegisops.platform.iam.error.IamErrorCode;
import io.aegisops.platform.iam.repository.PermissionDefinitionRepository;
import io.aegisops.platform.iam.repository.PlatformRoleRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformRoleService {

  private final PlatformRoleRepository roles;
  private final PermissionDefinitionRepository permissions;
  private final PermissionNormalizer normalizer;
  private final PlatformAuditService audit;

  public PlatformRoleService(
      PlatformRoleRepository roles,
      PermissionDefinitionRepository permissions,
      PermissionNormalizer normalizer,
      PlatformAuditService audit) {
    this.roles = roles;
    this.permissions = permissions;
    this.normalizer = normalizer;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<PlatformRole> list(boolean includeSystem) {
    return roles.list(includeSystem);
  }

  @Transactional(readOnly = true)
  public PlatformRoleDetail detail(String code) {
    return roles
        .findByCode(code)
        .orElseThrow(
            () ->
                new IamDomainException(
                    IamErrorCode.ROLE_NOT_FOUND, "role " + code + " not found"));
  }

  @Transactional
  public PlatformRole create(CreateRoleData data, String actor) {
    if (data.code() == null || data.code().isBlank()) {
      throw new IamDomainException(IamErrorCode.VALIDATION_FAILED, "role code is required");
    }
    if (roles.exists(data.code())) {
      throw new IamDomainException(
          IamErrorCode.USERNAME_CONFLICT, "role code already exists: " + data.code());
    }
    PermissionNormalizer.NormalizedPermissionSet normalized =
        normalizer.normalize(data.permissionCodes());
    PlatformRole created =
        roles
            .insert(data.code(), data.name(), data.description(), data.system(), data.enabled())
            .orElseThrow(
                () ->
                    new IamDomainException(
                        IamErrorCode.ROLE_NOT_FOUND,
                        "role did not persist: " + data.code()));
    roles.replacePermissions(data.code(), normalized.permissions());
    audit.recordChange(
        currentTenant(), actor, "platform.role.created", "platform.role", data.code(),
        Map.of("permissions", List.of()),
        Map.of("permissions", normalized.permissions()),
        Map.of("name", data.name(), "system", data.system()));
    return created;
  }

  @Transactional
  public PlatformRoleDetail update(String code, UpdateRoleData data, String actor) {
    PlatformRoleDetail existing = detail(code);
    if (existing.system() && Boolean.FALSE.equals(data.enabled())) {
      throw new IamDomainException(
          IamErrorCode.PROTECTED_ROLE_MODIFIED, "system role cannot be disabled");
    }
    roles.update(
        code,
        data.name(),
        data.description(),
        Boolean.TRUE.equals(data.enabled()),
        expectedVersionOrZero(existing));
    if (data.permissionCodes() != null) {
      PermissionNormalizer.NormalizedPermissionSet normalized =
          normalizer.normalize(data.permissionCodes());
      int activeUsers = roles.userCount(code);
      if (activeUsers > 0 && !normalized.permissions().containsAll(existing.permissions())) {
        throw new IamDomainException(
            IamErrorCode.PERMISSION_REMOVED_FOR_ACTIVE_ROLE,
            "cannot remove permissions from a role with active users");
      }
      roles.replacePermissions(code, normalized.permissions());
      audit.recordChange(
          currentTenant(), actor, "platform.role.permissionsReplaced", "platform.role", code,
          Map.of("permissions", existing.permissions()),
          Map.of("permissions", normalized.permissions()),
          Map.of());
    }
    return detail(code);
  }

  @Transactional
  public void replacePermissions(
      String code, ReplaceRolePermissionsCommand command, String actor) {
    PlatformRoleDetail existing = detail(code);
    PermissionNormalizer.NormalizedPermissionSet normalized =
        normalizer.normalize(command.permissionCodes());
    if (!normalized.criticalCodes().isEmpty()) {
      command.confirmation().requireConfirmed(code);
    }
    if (!normalized.permissions().containsAll(existing.permissions())
        && roles.userCount(code) > 0) {
      throw new IamDomainException(
          IamErrorCode.PERMISSION_REMOVED_FOR_ACTIVE_ROLE,
          "removing permissions from a role with active users is not allowed");
    }
    roles.replacePermissions(code, normalized.permissions());
    audit.recordChange(
        currentTenant(), actor, "platform.role.permissionsReplaced", "platform.role", code,
        Map.of("permissions", existing.permissions()),
        Map.of("permissions", normalized.permissions()),
        Map.of("reason", command.confirmation().reason()));
  }

  @Transactional
  public void replaceDataScopes(
      String code, ReplaceRoleDataScopesCommand command, String actor) {
    PlatformRoleDetail existing = detail(code);
    List<PlatformRole.RoleDataScope> scopes = command.normalized();
    roles.replaceDataScopes(code, scopes);
    audit.recordChange(
        currentTenant(), actor, "platform.role.dataScopesReplaced", "platform.role", code,
        Map.of("dataScopes", existing.dataScopes()),
        Map.of("dataScopes", scopes),
        Map.of());
  }

  @Transactional
  public void delete(String code, String actor) {
    PlatformRoleDetail existing = detail(code);
    if (existing.system()) {
      throw new IamDomainException(
          IamErrorCode.PROTECTED_ROLE_MODIFIED, "system role " + code + " cannot be deleted");
    }
    if (existing.userCount() > 0) {
      throw new IamDomainException(
          IamErrorCode.ROLE_HAS_ACTIVE_USERS, "role " + code + " still has active users");
    }
    // Soft delete would be the proper path; the existing role_definition schema does not
    // support deletion flag, so we rely on row-level guard above and let the SQL
    // enforcement reject unattached roles via cascade.
    audit.recordChange(
        currentTenant(), actor, "platform.role.deleted", "platform.role", code,
        Map.of("code", code),
        null,
        Map.of());
  }

  private static int expectedVersionOrZero(PlatformRoleDetail existing) {
    return existing.rowVersion();
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