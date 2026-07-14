package io.aegisops.platform.iam.repository;

import io.aegisops.platform.iam.domain.PlatformRole;
import io.aegisops.platform.iam.domain.PlatformRoleDetail;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPlatformRoleRepository implements PlatformRoleRepository {

  private final JdbcTemplate jdbc;

  public JdbcPlatformRoleRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<PlatformRole> list(boolean includeSystem) {
    String sql =
        """
            select code, name, description, is_system, enabled
              from iam.role_definition
            """
            + (includeSystem ? "" : " where is_system = false ")
            + " order by is_system desc, code asc ";
    List<PlatformRole> roles = jdbc.query(sql, ROLE_MAPPER);
    Map<String, Set<String>> perms = rolePermissionsAsMap();
    Map<String, Map<String, PlatformRole.RoleDataScope>> scopes = roleDataScopesByResource();
    return roles.stream()
        .map(
            role ->
                new PlatformRole(
                    role.code(),
                    role.name(),
                    role.description(),
                    role.system(),
                    role.enabled(),
                    perms.getOrDefault(role.code(), Collections.emptySet()),
                    scopes.getOrDefault(role.code(), Collections.emptyMap()),
                    0))
        .toList();
  }

  @Override
  public Optional<PlatformRoleDetail> findByCode(String code) {
    List<PlatformRole> rows =
        jdbc.query(
            """
                select code, name, description, is_system, enabled
                  from iam.role_definition
                 where code = ?
                """,
            ROLE_MAPPER,
            code);
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    PlatformRole role = rows.get(0);
    Set<String> permissions = listRolePermissions(code);
    Map<String, PlatformRoleDetail.RoleDataScope> dataScopes = listRoleDataScopesAsMap(code);
    Integer count =
        jdbc.queryForObject(
            " select count(*) from iam.user_role where role_code = ? ", Integer.class, code);
    return Optional.of(
        new PlatformRoleDetail(
            role.code(),
            role.name(),
            role.description(),
            role.system(),
            role.enabled(),
            permissions,
            dataScopes,
            count == null ? 0 : count,
            1));
  }

  @Override
  public int userCount(String code) {
    Integer count =
        jdbc.queryForObject(
            " select count(*) from iam.user_role where role_code = ? ", Integer.class, code);
    return count == null ? 0 : count;
  }

  @Override
  public Optional<PlatformRole> insert(
      String code, String name, String description, boolean system, boolean enabled) {
    int rows =
        jdbc.update(
            """
                insert into iam.role_definition(code, name, description, is_system, enabled, created_at, updated_at)
                values (?, ?, ?, ?, ?, now(), now())
                on conflict (code) do nothing
                """,
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
            code,
            name,
            description,
            system,
            enabled,
            Collections.emptySet(),
            Collections.emptyMap(),
            0));
  }

  @Override
  public boolean exists(String code) {
    Integer count =
        jdbc.queryForObject(
            " select count(*) from iam.role_definition where code = ? ", Integer.class, code);
    return count != null && count > 0;
  }

  @Override
  public int update(
      String code, String name, String description, boolean enabled, int expectedVersion) {
    return jdbc.update(
        """
            update iam.role_definition
               set name = coalesce(?, name),
                   description = coalesce(?, description),
                   enabled = ?,
                   updated_at = now()
             where code = ?
            """,
        name,
        description,
        enabled,
        code);
  }

  @Override
  public int replacePermissions(String code, Set<String> permissionCodes) {
    jdbc.update(" delete from iam.role_permission where role_code = ? ", code);
    if (permissionCodes == null || permissionCodes.isEmpty()) {
      return 0;
    }
    int total = 0;
    for (String permission : permissionCodes) {
      total +=
          jdbc.update(
              """
                  insert into iam.role_permission(tenant_id, role_code, permission_code, granted_at)
                  values ('default', ?, ?, now())
                  on conflict (role_code, permission_code) do nothing
                  """,
              code,
              permission);
    }
    return total;
  }

  @Override
  public int replaceDataScopes(String code, List<PlatformRole.RoleDataScope> scopes) {
    jdbc.update(" delete from iam.role_data_scope_v2 where role_code = ? ", code);
    if (scopes == null || scopes.isEmpty()) {
      return 0;
    }
    int total = 0;
    for (PlatformRole.RoleDataScope scope : scopes) {
      total +=
          jdbc.update(
              """
                  insert into iam.role_data_scope_v2(tenant_id, role_code, resource_code, scope_type, scope_json)
                  values ('default', ?, ?, ?, ?::jsonb)
                  on conflict (role_code, resource_code) do nothing
                  """,
              code,
              scope.resourceCode(),
              scope.scopeType(),
              scopeJson(scope.detail()));
    }
    return total;
  }

  @Override
  public void seedDefaultRolePermissions() {
    // No-op: seeding happens through Flyway migration. Kept for future bootstrap.
  }

  private Map<String, Set<String>> rolePermissionsAsMap() {
    Map<String, Set<String>> map = new HashMap<>();
    jdbc.query(
        " select role_code, permission_code from iam.role_permission ",
        rs -> {
          map.computeIfAbsent(rs.getString("role_code"), k -> new HashSet<>())
              .add(rs.getString("permission_code"));
        });
    return map;
  }

  private Map<String, Map<String, PlatformRole.RoleDataScope>> roleDataScopesByResource() {
    Map<String, Map<String, PlatformRole.RoleDataScope>> result = new HashMap<>();
    jdbc.query(
        """
            select role_code, resource_code, scope_type, scope_json
              from iam.role_data_scope_v2
            """,
        rs -> {
          String role = rs.getString("role_code");
          String resource = rs.getString("resource_code");
          String type = rs.getString("scope_type");
          Map<String, Object> detail = parseJson(rs.getString("scope_json"));
          result
              .computeIfAbsent(role, k -> new HashMap<>())
              .put(resource, new PlatformRole.RoleDataScope(resource, type, detail));
        });
    return result;
  }

  private Set<String> listRolePermissions(String code) {
    return new HashSet<>(
        jdbc.query(
            " select permission_code from iam.role_permission where role_code = ? ",
            (rs, rowNum) -> rs.getString(1),
            code));
  }

  private Map<String, PlatformRoleDetail.RoleDataScope> listRoleDataScopesAsMap(String code) {
    Map<String, PlatformRoleDetail.RoleDataScope> result = new LinkedHashMap<>();
    jdbc.query(
        """
            select resource_code, scope_type, scope_json
              from iam.role_data_scope_v2
             where role_code = ?
            """,
        rs -> {
          String resource = rs.getString("resource_code");
          String scopeType = rs.getString("scope_type");
          String raw = rs.getString("scope_json");
          result.put(
              resource, new PlatformRoleDetail.RoleDataScope(resource, scopeType, parseJson(raw)));
        },
        code);
    return result;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> parseJson(String raw) {
    if (raw == null || raw.isBlank()) {
      return Collections.emptyMap();
    }
    try {
      com.fasterxml.jackson.databind.ObjectMapper m =
          new com.fasterxml.jackson.databind.ObjectMapper();
      return (Map<String, Object>) m.readValue(raw, Map.class);
    } catch (Exception ex) {
      return Collections.emptyMap();
    }
  }

  private static String scopeJson(Map<String, Object> detail) {
    if (detail == null || detail.isEmpty()) {
      return "{}";
    }
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(detail);
    } catch (Exception ex) {
      return "{}";
    }
  }

  private static final RowMapper<PlatformRole> ROLE_MAPPER =
      (rs, rowNum) ->
          new PlatformRole(
              rs.getString("code"),
              rs.getString("name"),
              rs.getString("description"),
              rs.getBoolean("is_system"),
              rs.getBoolean("enabled"),
              Collections.emptySet(),
              Collections.emptyMap(),
              0);
}
