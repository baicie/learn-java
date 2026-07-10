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
          while (rs.next()) {
            roles.add(rs.getString("role_code"));
            String perm = rs.getString("permission_code");
            if (perm != null && !perm.isBlank()) {
              permissions.add(perm);
            }
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
        (ResultSet rs) -> collectScopes(rs, dataScopes));

    return new AuthorizationSnapshot(roles, permissions, dataScopes);
  }

  public void assignRole(String tenantId, String userId, String roleCode, String actor) {
    requireText(tenantId, "tenantId");
    requireText(userId, "userId");
    if (!BuiltInRoleCodes.ALL.contains(roleCode)) {
      throw new IllegalArgumentException("unknown built-in role: " + roleCode);
    }
    jdbc.update(
        """
        insert into iam.user_role(tenant_id, user_id, role_code, created_by)
        values (:tenantId, :userId, :roleCode, :actor)
        on conflict do nothing
        """,
        Map.of("tenantId", tenantId, "userId", userId, "roleCode", roleCode, "actor", actor == null ? "system" : actor));
  }

  public void removeRole(String tenantId, String userId, String roleCode) {
    jdbc.update(
        "delete from iam.user_role where tenant_id = :tenantId and user_id = :userId and role_code = :roleCode",
        Map.of("tenantId", tenantId, "userId", userId, "roleCode", roleCode));
  }

  private void collectScopes(ResultSet rs, Map<String, DataScope> target) throws SQLException {
    while (rs.next()) {
      String code = rs.getString("resource_code");
      DataScope scope = DataScope.from(rs.getString("scope_type"));
      target.merge(code, scope, DataScope::max);
    }
  }

  private void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
  }
}
