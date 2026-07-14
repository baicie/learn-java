package io.aegisops.security;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthorizationRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public AuthorizationRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public AuthorizationSnapshot findSnapshot(String tenantId, String userId) {
    requireText(tenantId, "tenantId");
    requireText(userId, "userId");

    Map<String, Object> params = Map.of("tenantId", tenantId, "userId", userId);
    Set<String> roles = new LinkedHashSet<>();
    Set<String> permissions = new LinkedHashSet<>();

    jdbc.query(
        """
        select distinct r.role_code, p.permission_code
          from iam.user_role ur
          join iam.role_definition r on r.role_code = ur.role_code and r.enabled = true
          left join iam.role_permission rp on rp.role_code = r.role_code
          left join iam.permission p on p.permission_code = rp.permission_code and p.enabled = true
         where ur.tenant_id = :tenantId and ur.user_id = :userId
        """,
        params,
        (ResultSet rs) -> {
          roles.add(rs.getString("role_code"));
          String perm = rs.getString("permission_code");
          if (perm != null && !perm.isBlank()) {
            permissions.add(perm);
          }
        });

    Map<String, DataScope> dataScopes = new LinkedHashMap<>();
    jdbc.query(
        """
        select ds.resource_code, ds.scope_type
          from iam.user_role ur
          join iam.role_definition r on r.role_code = ur.role_code and r.enabled = true
          join iam.role_data_scope ds on ds.role_code = r.role_code
         where ur.tenant_id = :tenantId and ur.user_id = :userId
        """,
        params,
        (ResultSet rs) -> collectScope(rs, dataScopes));

    return new AuthorizationSnapshot(roles, permissions, dataScopes);
  }

  public void assignRole(String tenantId, String userId, String roleCode, String actor) {
    requireText(tenantId, "tenantId");
    requireText(userId, "userId");
    requireText(roleCode, "roleCode");

    Integer userCount =
        jdbc.queryForObject(
            "select count(*) from sys_user where tenant_id = :tenantId and id = :userId",
            Map.of("tenantId", tenantId, "userId", userId),
            Integer.class);

    if (userCount == null || userCount != 1) {
      throw new IllegalArgumentException("user not found in tenant");
    }

    Integer roleCount =
        jdbc.queryForObject(
            "select count(*) from iam.role_definition where role_code = :roleCode and enabled = true",
            Map.of("roleCode", roleCode),
            Integer.class);

    if (roleCount == null || roleCount != 1) {
      throw new IllegalArgumentException("role not found or disabled: " + roleCode);
    }

    jdbc.update(
        """
        insert into iam.user_role(tenant_id, user_id, role_code, created_by)
        values (:tenantId, :userId, :roleCode, :actor)
        on conflict do nothing
        """,
        Map.of(
            "tenantId",
            tenantId,
            "userId",
            userId,
            "roleCode",
            roleCode,
            "actor",
            actor == null ? "system" : actor));
  }

  public int migrateLegacyAssignments(String tenantId, String userId) {
    requireText(tenantId, "tenantId");
    requireText(userId, "userId");

    return jdbc.update(
        """
        with mapped_roles as (
            select distinct
                u.tenant_id,
                u.id as user_id,
                case lower(r.code)
                    when 'admin' then 'system_admin'
                    when 'operator' then 'ops_operator'
                    when 'viewer' then 'readonly_user'
                    when 'readonly' then 'readonly_user'
                    when 'readonly_user' then 'readonly_user'
                    when 'normal_user' then 'normal_user'
                    when 'record_admin' then 'record_admin'
                    when 'system_admin' then 'system_admin'
                    else null
                end as new_role_code
            from sys_user u
            join sys_user_role ur on ur.user_id = u.id
            join sys_role r on r.id = ur.role_id
            where u.tenant_id = :tenantId and u.id = :userId
        )
        insert into iam.user_role(tenant_id, user_id, role_code, created_by)
        select tenant_id, user_id, new_role_code, 'legacy-role-fallback'
        from mapped_roles
        where new_role_code is not null
        on conflict do nothing
        """,
        Map.of("tenantId", tenantId, "userId", userId));
  }

  public void removeRole(String tenantId, String userId, String roleCode) {
    jdbc.update(
        "delete from iam.user_role where tenant_id = :tenantId and user_id = :userId and role_code = :roleCode",
        Map.of("tenantId", tenantId, "userId", userId, "roleCode", roleCode));
  }

  private void collectScope(ResultSet rs, Map<String, DataScope> target) throws SQLException {
    String code = rs.getString("resource_code");
    DataScope scope = DataScope.from(rs.getString("scope_type"));
    target.merge(code, scope, DataScope::max);
  }

  private void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
  }
}
