package io.aegisops.platform.iam.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.platform.iam.domain.PlatformRole;
import io.aegisops.platform.iam.domain.PlatformRoleDetail;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPlatformRoleRepository implements PlatformRoleRepository {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final JdbcTemplate jdbc;

  public JdbcPlatformRoleRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<PlatformRole> list(boolean includeSystem) {
    String tenantId = currentTenant();
    String sql =
        """
            select role_code, role_name, description, system_builtin, enabled, row_version
              from iam.role_definition
             where tenant_id = ? and deleted_at is null
            """
            + (includeSystem ? "" : " and system_builtin = false ")
            + " order by system_builtin desc, role_code asc ";
    return jdbc.query(
        sql,
        (rs, rowNum) -> {
          String code = rs.getString("role_code");
          return new PlatformRole(
              code,
              rs.getString("role_name"),
              rs.getString("description"),
              rs.getBoolean("system_builtin"),
              rs.getBoolean("enabled"),
              listRolePermissions(tenantId, code),
              listRoleDataScopes(tenantId, code),
              userCount(tenantId, code),
              rs.getInt("row_version"));
        },
        tenantId);
  }

  @Override
  public Optional<PlatformRoleDetail> findByCode(String code) {
    String tenantId = currentTenant();
    return jdbc
        .query(
            """
                select role_code, role_name, description, system_builtin, enabled, row_version
                  from iam.role_definition
                 where tenant_id = ? and role_code = ? and deleted_at is null
                """,
            (rs, rowNum) ->
                new PlatformRoleDetail(
                    rs.getString("role_code"),
                    rs.getString("role_name"),
                    rs.getString("description"),
                    rs.getBoolean("system_builtin"),
                    rs.getBoolean("enabled"),
                    listRolePermissions(tenantId, code),
                    listRoleDataScopesAsDetail(tenantId, code),
                    userCount(tenantId, code),
                    rs.getInt("row_version")),
            tenantId,
            code)
        .stream()
        .findFirst();
  }

  @Override
  public int userCount(String code) {
    return userCount(currentTenant(), code);
  }

  @Override
  public Optional<PlatformRole> insert(
      String code, String name, String description, boolean system, boolean enabled) {
    String tenantId = currentTenant();
    int rows =
        jdbc.update(
            """
                insert into iam.role_definition(
                    tenant_id, role_code, role_name, description, system_builtin, enabled)
                values (?, ?, ?, ?, ?, ?)
                on conflict (tenant_id, role_code) do nothing
                """,
            tenantId,
            code,
            name,
            description,
            system,
            enabled);
    if (rows == 0) {
      return Optional.empty();
    }
    return Optional.of(
        new PlatformRole(
            code, name, description, system, enabled, Set.of(), Collections.emptyMap(), 0, 1));
  }

  @Override
  public boolean exists(String code) {
    Integer count =
        jdbc.queryForObject(
            """
                select count(*) from iam.role_definition
                 where tenant_id = ? and role_code = ? and deleted_at is null
                """,
            Integer.class,
            currentTenant(),
            code);
    return count != null && count > 0;
  }

  @Override
  public int update(
      String code, String name, String description, boolean enabled, int expectedVersion) {
    return jdbc.update(
        """
            update iam.role_definition
               set role_name = coalesce(?, role_name),
                   description = coalesce(?, description),
                   enabled = ?, updated_at = now(), row_version = row_version + 1
             where tenant_id = ? and role_code = ? and row_version = ? and deleted_at is null
            """,
        name,
        description,
        enabled,
        currentTenant(),
        code,
        expectedVersion);
  }

  @Override
  public int replacePermissions(String code, Set<String> permissionCodes) {
    String tenantId = currentTenant();
    jdbc.update(
        "delete from iam.role_permission where tenant_id = ? and role_code = ?", tenantId, code);
    if (permissionCodes == null || permissionCodes.isEmpty()) {
      return 0;
    }
    int total = 0;
    for (String permission : permissionCodes) {
      total +=
          jdbc.update(
              """
                  insert into iam.role_permission(tenant_id, role_code, permission_code)
                  values (?, ?, ?)
                  on conflict (tenant_id, role_code, permission_code) do nothing
                  """,
              tenantId,
              code,
              permission);
    }
    return total;
  }

  @Override
  public int replaceDataScopes(String code, List<PlatformRole.RoleDataScope> scopes) {
    String tenantId = currentTenant();
    jdbc.update(
        "delete from iam.role_data_scope_v2 where tenant_id = ? and role_code = ?", tenantId, code);
    if (scopes == null || scopes.isEmpty()) {
      return 0;
    }
    int total = 0;
    for (PlatformRole.RoleDataScope scope : scopes) {
      total +=
          jdbc.update(
              """
                  insert into iam.role_data_scope_v2(
                      tenant_id, role_code, resource_code, scope_type, scope_json)
                  values (?, ?, ?, ?, ?::jsonb)
                  on conflict (tenant_id, role_code, resource_code) do update
                  set scope_type = excluded.scope_type,
                      scope_json = excluded.scope_json,
                      updated_at = now()
                  """,
              tenantId,
              code,
              scope.resourceCode(),
              scope.scopeType(),
              toJson(scope.detail()));
    }
    return total;
  }

  @Override
  public int softDelete(String code, int expectedVersion) {
    return jdbc.update(
        """
            update iam.role_definition
               set enabled = false, deleted_at = now(), updated_at = now(),
                   row_version = row_version + 1
             where tenant_id = ? and role_code = ? and row_version = ? and deleted_at is null
            """,
        currentTenant(),
        code,
        expectedVersion);
  }

  @Override
  public void seedDefaultRolePermissions() {
    // Flyway owns permission seeding.
  }

  private int userCount(String tenantId, String code) {
    Integer count =
        jdbc.queryForObject(
            "select count(*) from iam.user_role where tenant_id = ? and role_code = ?",
            Integer.class,
            tenantId,
            code);
    return count == null ? 0 : count;
  }

  private Set<String> listRolePermissions(String tenantId, String code) {
    return new LinkedHashSet<>(
        jdbc.query(
            """
                select permission_code from iam.role_permission
                 where tenant_id = ? and role_code = ? order by permission_code
                """,
            (rs, rowNum) -> rs.getString(1),
            tenantId,
            code));
  }

  private Map<String, PlatformRole.RoleDataScope> listRoleDataScopes(String tenantId, String code) {
    Map<String, PlatformRole.RoleDataScope> result = new LinkedHashMap<>();
    jdbc.query(
        """
            select resource_code, scope_type, scope_json
              from iam.role_data_scope_v2
             where tenant_id = ? and role_code = ? order by resource_code
            """,
        rs -> {
          String resource = rs.getString("resource_code");
          result.put(
              resource,
              new PlatformRole.RoleDataScope(
                  resource, rs.getString("scope_type"), parseJson(rs.getString("scope_json"))));
        },
        tenantId,
        code);
    return result;
  }

  private Map<String, PlatformRoleDetail.RoleDataScope> listRoleDataScopesAsDetail(
      String tenantId, String code) {
    Map<String, PlatformRoleDetail.RoleDataScope> result = new LinkedHashMap<>();
    listRoleDataScopes(tenantId, code)
        .forEach(
            (resource, scope) ->
                result.put(
                    resource,
                    new PlatformRoleDetail.RoleDataScope(
                        resource, scope.scopeType(), scope.detail())));
    return result;
  }

  private static Map<String, Object> parseJson(String value) {
    try {
      return value == null
          ? Map.of()
          : JSON.readValue(value, new TypeReference<Map<String, Object>>() {});
    } catch (Exception exception) {
      throw new IllegalStateException("invalid role data scope JSON", exception);
    }
  }

  private static String toJson(Map<String, Object> value) {
    try {
      return JSON.writeValueAsString(value == null ? Map.of() : value);
    } catch (Exception exception) {
      throw new IllegalArgumentException("invalid role data scope", exception);
    }
  }

  private String currentTenant() {
    return SecurityContextSupport.currentTenant();
  }
}
