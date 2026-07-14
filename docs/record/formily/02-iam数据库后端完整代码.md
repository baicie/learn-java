---
title: IAM 数据库、用户与 RBAC 后端完整代码
type: design
status: draft
phase: work-record
owner: ai
created: 2026-07-14
updated: 2026-07-14
related: []
---

# IAM 数据库、用户与 RBAC 后端完整代码

## 1. 迁移

新迁移编号应在当前最新 Flyway 版本后顺延。以下文件示例名：

```text
V0028__portal_iam_management.sql
```

```sql
create schema if not exists iam;

alter table sys_user
    add column if not exists status varchar(32) not null default 'active',
    add column if not exists locked_until timestamptz,
    add column if not exists failed_login_count integer not null default 0,
    add column if not exists last_login_at timestamptz,
    add column if not exists password_changed_at timestamptz,
    add column if not exists row_version integer not null default 1;

create unique index if not exists uk_sys_user_tenant_username_lower
    on sys_user(tenant_id, lower(username));

create index if not exists idx_sys_user_tenant_status_created
    on sys_user(tenant_id, status, created_at desc, id desc);

alter table iam.role
    add column if not exists description varchar(500),
    add column if not exists enabled boolean not null default true,
    add column if not exists system boolean not null default false,
    add column if not exists row_version integer not null default 1,
    add column if not exists updated_at timestamptz not null default now();

create table if not exists iam.permission_definition (
    permission_code varchar(128) primary key,
    module_code varchar(64) not null,
    permission_name varchar(128) not null,
    description varchar(500),
    risk_level varchar(32) not null default 'normal',
    dependencies_json jsonb not null default '[]'::jsonb,
    sort_order integer not null default 0,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_permission_risk
        check (risk_level in ('normal', 'sensitive', 'high', 'critical')),
    constraint ck_permission_dependencies_array
        check (jsonb_typeof(dependencies_json) = 'array')
);

create table if not exists iam.role_data_scope (
    tenant_id varchar(64) not null,
    role_code varchar(64) not null,
    resource_code varchar(64) not null,
    scope_type varchar(32) not null,
    scope_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (tenant_id, role_code, resource_code),
    constraint ck_role_scope_type
        check (scope_type in ('ALL', 'SELF', 'DEPARTMENT', 'CUSTOM')),
    constraint ck_role_scope_json_object
        check (jsonb_typeof(scope_json) = 'object')
);

create index if not exists idx_user_role_tenant_role
    on iam.user_role(tenant_id, role_code, user_id);

insert into iam.permission_definition(
    permission_code, module_code, permission_name, risk_level,
    dependencies_json, sort_order
)
values
    ('platform:user:read', 'platform-user', '查看用户', 'sensitive', '[]', 10),
    ('platform:user:write', 'platform-user', '创建和编辑用户', 'high',
     '["platform:user:read"]', 20),
    ('platform:user:status', 'platform-user', '启用或禁用用户', 'critical',
     '["platform:user:read"]', 30),
    ('platform:user:assign-role', 'platform-user', '分配角色', 'critical',
     '["platform:user:read","platform:role:read"]', 40),
    ('platform:user:reset-password', 'platform-user', '重置密码', 'critical',
     '["platform:user:read"]', 50),
    ('platform:role:read', 'platform-role', '查看角色', 'sensitive', '[]', 10),
    ('platform:role:write', 'platform-role', '管理角色权限', 'critical',
     '["platform:role:read"]', 20)
on conflict (permission_code) do update
set module_code = excluded.module_code,
    permission_name = excluded.permission_name,
    risk_level = excluded.risk_level,
    dependencies_json = excluded.dependencies_json,
    updated_at = now();
```

## 2. Domain

```java
package io.aegisops.platform.iam.domain;

public enum PlatformUserStatus {
  ACTIVE,
  DISABLED,
  LOCKED,
  PENDING;

  public String value() {
    return name().toLowerCase();
  }

  public static PlatformUserStatus from(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("user status is required");
    }
    return valueOf(value.trim().toUpperCase());
  }
}
```

```java
package io.aegisops.platform.iam.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record PlatformUser(
    String id,
    String tenantId,
    String username,
    String displayName,
    String email,
    PlatformUserStatus status,
    List<RoleRef> roles,
    Map<String, String> dataScopes,
    OffsetDateTime lastLoginAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    int rowVersion) {

  public record RoleRef(String code, String name) {}
}
```

```java
package io.aegisops.platform.iam.domain;

public enum PermissionRisk {
  NORMAL,
  SENSITIVE,
  HIGH,
  CRITICAL
}
```

```java
package io.aegisops.platform.iam.domain;

import java.util.Set;

public record PermissionDefinition(
    String code,
    String moduleCode,
    String name,
    String description,
    PermissionRisk risk,
    Set<String> dependencies,
    int sortOrder,
    boolean enabled) {}
```

## 3. Repository Port

```java
package io.aegisops.platform.iam.application.port;

public interface PlatformUserRepository {
  PlatformUserPage page(String tenantId, PlatformUserQuery query);
  Optional<PlatformUser> findById(String tenantId, String userId);
  Optional<PlatformUser> findByUsername(String tenantId, String username);
  PlatformUser create(String tenantId, CreatePlatformUserData data);
  boolean update(String tenantId, String userId, int expectedVersion,
                 UpdatePlatformUserData data);
  boolean updateStatus(String tenantId, String userId, int expectedVersion,
                       PlatformUserStatus status);
  void replaceRoles(String tenantId, String userId, Set<String> roleCodes,
                    String actorId);
  long countActiveSystemAdmins(String tenantId);
  boolean hasRole(String tenantId, String userId, String roleCode);
}
```

```java
package io.aegisops.platform.iam.application.port;

public interface PlatformRoleRepository {
  List<PlatformRole> list(String tenantId);
  Optional<PlatformRoleDetail> find(String tenantId, String roleCode);
  PlatformRole create(String tenantId, CreateRoleData data);
  boolean update(String tenantId, String roleCode, int expectedVersion,
                 UpdateRoleData data);
  boolean delete(String tenantId, String roleCode, int expectedVersion);
  Set<String> permissions(String tenantId, String roleCode);
  void replacePermissions(String tenantId, String roleCode,
                          Set<String> permissionCodes);
  Map<String, RoleDataScope> dataScopes(String tenantId, String roleCode);
  void replaceDataScopes(String tenantId, String roleCode,
                         Collection<RoleDataScope> scopes);
  int userCount(String tenantId, String roleCode);
}
```

## 4. PermissionNormalizer

```java
package io.aegisops.platform.iam.application;

import io.aegisops.platform.iam.application.port.PermissionDefinitionRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PermissionNormalizer {

  private final PermissionDefinitionRepository repository;

  public PermissionNormalizer(PermissionDefinitionRepository repository) {
    this.repository = repository;
  }

  public Set<String> normalize(Set<String> requested) {
    LinkedHashSet<String> result = new LinkedHashSet<>(requested);
    boolean changed;

    do {
      changed = false;
      for (String code : List.copyOf(result)) {
        PermissionDefinition definition =
            repository.findEnabled(code)
                .orElseThrow(() ->
                    new IllegalArgumentException("unknown permission: " + code));

        for (String dependency : definition.dependencies()) {
          changed |= result.add(dependency);
        }
      }
    } while (changed);

    return Set.copyOf(result);
  }
}
```

## 5. User Service

```java
package io.aegisops.platform.iam.application;

@Service
public class PlatformUserService {

  private final PlatformUserRepository users;
  private final PlatformRoleRepository roles;
  private final PasswordEncoder passwordEncoder;
  private final PlatformAuditService auditService;

  public PlatformUserService(
      PlatformUserRepository users,
      PlatformRoleRepository roles,
      PasswordEncoder passwordEncoder,
      PlatformAuditService auditService) {
    this.users = users;
    this.roles = roles;
    this.passwordEncoder = passwordEncoder;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public PlatformUserPage page(
      String tenantId,
      PlatformUserQuery query,
      UserPrincipal actor) {
    require(actor, "platform:user:read");
    return users.page(tenantId, query.normalized());
  }

  @Transactional
  public PlatformUser create(
      String tenantId,
      CreatePlatformUserCommand command,
      UserPrincipal actor) {
    require(actor, "platform:user:write");
    command.validate();

    if (users.findByUsername(tenantId, command.username()).isPresent()) {
      throw new ConflictException("username already exists");
    }

    validateAssignableRoles(tenantId, actor, command.roleCodes());

    PlatformUser created = users.create(
        tenantId,
        new CreatePlatformUserData(
            command.username().trim(),
            command.displayName().trim(),
            blankToNull(command.email()),
            passwordEncoder.encode(command.initialPassword()),
            PlatformUserStatus.ACTIVE));

    users.replaceRoles(
        tenantId,
        created.id(),
        Set.copyOf(command.roleCodes()),
        actor.id());

    PlatformUser result = requireUser(tenantId, created.id());

    auditService.recordChange(
        tenantId,
        actor.id(),
        "platform_user",
        result.id(),
        "platform.user.create",
        Map.of(),
        auditSnapshot(result),
        Map.of());

    return result;
  }

  @Transactional
  public PlatformUser changeStatus(
      String tenantId,
      String userId,
      ChangeUserStatusCommand command,
      UserPrincipal actor) {
    require(actor, "platform:user:status");

    PlatformUser before = requireUser(tenantId, userId);
    PlatformUserStatus target = PlatformUserStatus.from(command.status());

    if (actor.id().equals(userId) && target == PlatformUserStatus.DISABLED) {
      throw new ConflictException("current user cannot disable itself");
    }

    if (before.roles().stream().anyMatch(r -> r.code().equals("system_admin"))
        && target != PlatformUserStatus.ACTIVE
        && users.countActiveSystemAdmins(tenantId) <= 1) {
      throw new ConflictException("last active system administrator is required");
    }

    boolean changed = users.updateStatus(
        tenantId, userId, command.rowVersion(), target);

    if (!changed) {
      throw new ConflictException("user was changed by another request");
    }

    PlatformUser after = requireUser(tenantId, userId);

    auditService.recordChange(
        tenantId,
        actor.id(),
        "platform_user",
        userId,
        "platform.user.status.change",
        auditSnapshot(before),
        auditSnapshot(after),
        Map.of("reason", command.reason()));

    return after;
  }

  @Transactional
  public PlatformUser replaceRoles(
      String tenantId,
      String userId,
      ReplaceUserRolesCommand command,
      UserPrincipal actor) {
    require(actor, "platform:user:assign-role");

    PlatformUser before = requireUser(tenantId, userId);
    Set<String> target = Set.copyOf(command.roleCodes());
    validateAssignableRoles(tenantId, actor, target);

    boolean removingSystemAdmin =
        before.roles().stream().anyMatch(r -> r.code().equals("system_admin"))
            && !target.contains("system_admin");

    if (removingSystemAdmin && users.countActiveSystemAdmins(tenantId) <= 1) {
      throw new ConflictException("last active system administrator is required");
    }

    users.replaceRoles(tenantId, userId, target, actor.id());
    PlatformUser after = requireUser(tenantId, userId);

    auditService.recordChange(
        tenantId,
        actor.id(),
        "platform_user",
        userId,
        "platform.user.role.change",
        auditSnapshot(before),
        auditSnapshot(after),
        Map.of("reason", command.reason()));

    return after;
  }

  private void validateAssignableRoles(
      String tenantId,
      UserPrincipal actor,
      Collection<String> roleCodes) {
    for (String roleCode : roleCodes) {
      PlatformRoleDetail role =
          roles.find(tenantId, roleCode)
              .orElseThrow(() -> new ResourceNotFoundException(
                  "role not found: " + roleCode));

      if (!role.enabled()) {
        throw new ConflictException("role is disabled: " + roleCode);
      }

      if (role.system()
          && !actor.roles().contains("system_admin")) {
        throw new AccessDeniedException("system role cannot be assigned");
      }
    }
  }

  private void require(UserPrincipal actor, String permission) {
    if (actor == null || !actor.hasPermission(permission)) {
      throw new AccessDeniedException("access denied");
    }
  }
}
```

## 6. Role Service

核心规则：

```java
@Transactional
public PlatformRoleDetail replacePermissions(
    String tenantId,
    String roleCode,
    ReplaceRolePermissionsCommand command,
    UserPrincipal actor) {
  require(actor, "platform:role:write");

  PlatformRoleDetail before = requireRole(tenantId, roleCode);

  if (before.system()
      && roleCode.equals("system_admin")
      && !command.permissionCodes().containsAll(coreSystemPermissions)) {
    throw new ConflictException(
        "system administrator core permissions cannot be removed");
  }

  Set<String> normalized =
      permissionNormalizer.normalize(Set.copyOf(command.permissionCodes()));

  Set<String> dangerous =
      permissionDefinitions.findAll(normalized).stream()
          .filter(it -> it.risk() == HIGH || it.risk() == CRITICAL)
          .map(PermissionDefinition::code)
          .collect(toUnmodifiableSet());

  if (!dangerous.isEmpty()) {
    command.confirmation().requireConfirmed(roleCode);
  }

  validateGrantSubset(actor, normalized);

  roles.replacePermissions(tenantId, roleCode, normalized);
  PlatformRoleDetail after = requireRole(tenantId, roleCode);

  auditService.recordChange(
      tenantId,
      actor.id(),
      "platform_role",
      roleCode,
      "platform.role.permission.change",
      snapshot(before),
      snapshot(after),
      Map.of(
          "reason", command.confirmation().reason(),
          "dangerousPermissions", dangerous));

  return after;
}
```

## 7. Controller

```java
@RestController
@RequestMapping("/api/platform/users")
public class PlatformUserController {

  private final PlatformUserService service;

  @GetMapping
  @PreAuthorize("hasAuthority('platform:user:read')")
  public ApiResponse<PlatformUserPage> page(
      @Valid PlatformUserQuery query,
      @AuthenticationPrincipal UserPrincipal actor) {
    return ApiResponse.ok(service.page(actor.tenantId(), query, actor));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('platform:user:write')")
  public ApiResponse<PlatformUser> create(
      @Valid @RequestBody CreatePlatformUserCommand command,
      @AuthenticationPrincipal UserPrincipal actor) {
    return ApiResponse.ok(service.create(actor.tenantId(), command, actor));
  }

  @PutMapping("/{userId}/status")
  @PreAuthorize("hasAuthority('platform:user:status')")
  public ApiResponse<PlatformUser> status(
      @PathVariable String userId,
      @Valid @RequestBody ChangeUserStatusCommand command,
      @AuthenticationPrincipal UserPrincipal actor) {
    return ApiResponse.ok(
        service.changeStatus(actor.tenantId(), userId, command, actor));
  }

  @PutMapping("/{userId}/roles")
  @PreAuthorize("hasAuthority('platform:user:assign-role')")
  public ApiResponse<PlatformUser> roles(
      @PathVariable String userId,
      @Valid @RequestBody ReplaceUserRolesCommand command,
      @AuthenticationPrincipal UserPrincipal actor) {
    return ApiResponse.ok(
        service.replaceRoles(actor.tenantId(), userId, command, actor));
  }
}
```

角色 Controller 按同样风格提供：

```text
GET/POST/PUT/DELETE /api/platform/roles
GET /api/platform/permissions/tree
PUT /api/platform/roles/{code}/permissions
PUT /api/platform/roles/{code}/data-scopes
GET /api/platform/roles/{code}/users
```

## 8. 错误码

```text
PLATFORM_USER_NOT_FOUND                 404
PLATFORM_USER_USERNAME_CONFLICT         409
PLATFORM_USER_SELF_DISABLE_FORBIDDEN    409
PLATFORM_LAST_SYSTEM_ADMIN_REQUIRED     409
PLATFORM_ROLE_NOT_FOUND                 404
PLATFORM_ROLE_SYSTEM_DELETE_FORBIDDEN   409
PLATFORM_ROLE_VERSION_CONFLICT          409
PLATFORM_ROLE_PERMISSION_FORBIDDEN      403
```

## 9. 后端单元测试

```java
@ExtendWith(MockitoExtension.class)
class PlatformUserServiceTest {

  @Mock PlatformUserRepository users;
  @Mock PlatformRoleRepository roles;
  @Mock PasswordEncoder passwordEncoder;
  @Mock PlatformAuditService auditService;

  PlatformUserService service;

  @BeforeEach
  void setUp() {
    service = new PlatformUserService(
        users, roles, passwordEncoder, auditService);
  }

  @Test
  void cannotDisableCurrentUser() {
    UserPrincipal actor = admin("admin-1");
    PlatformUser current = user("admin-1", "system_admin", 1);

    when(users.findById("t1", "admin-1"))
        .thenReturn(Optional.of(current));

    assertThatThrownBy(() ->
        service.changeStatus(
            "t1",
            "admin-1",
            new ChangeUserStatusCommand("disabled", "test", 1),
            actor))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("cannot disable");
  }

  @Test
  void cannotRemoveLastSystemAdministrator() {
    UserPrincipal actor = admin("admin-1");
    PlatformUser current = user("admin-1", "system_admin", 1);

    when(users.findById("t1", "admin-1"))
        .thenReturn(Optional.of(current));
    when(users.countActiveSystemAdmins("t1")).thenReturn(1L);

    assertThatThrownBy(() ->
        service.replaceRoles(
            "t1",
            "admin-1",
            new ReplaceUserRolesCommand(
                List.of("normal_user"), "remove", 1),
            actor))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("last active");
  }
}
```

还必须增加：

```text
PermissionNormalizerTest
PlatformRoleServiceTest
PlatformUserControllerWebTest
PlatformRoleControllerWebTest
PlatformUserRepositoryPostgresIT
PlatformRoleRepositoryPostgresIT
```
