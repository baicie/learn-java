package io.aegisops.platform.iam.repository;

import io.aegisops.platform.iam.domain.PermissionDefinition;
import io.aegisops.platform.iam.domain.PermissionRisk;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPermissionDefinitionRepository implements PermissionDefinitionRepository {

  private final JdbcTemplate jdbc;

  public JdbcPermissionDefinitionRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<PermissionDefinition> list(String moduleCode) {
    String sql =
        """
            select permission_code, module_code, permission_name, description, risk_level,
                   dependencies_json, sort_order, enabled
              from iam.permission_definition
             where enabled = true
            """;
    String filterSql =
        moduleCode == null || moduleCode.isBlank()
            ? sql + " order by module_code, sort_order, permission_code"
            : sql + " and module_code = ? order by sort_order, permission_code";
    return moduleCode == null || moduleCode.isBlank()
        ? jdbc.query(filterSql, ROW_MAPPER)
        : jdbc.query(filterSql, ROW_MAPPER, moduleCode);
  }

  @Override
  public List<PermissionDefinition> listAll() {
    return jdbc.query(
        """
            select permission_code, module_code, permission_name, description, risk_level,
                   dependencies_json, sort_order, enabled
              from iam.permission_definition
             order by module_code, sort_order, permission_code
            """,
        ROW_MAPPER);
  }

  @Override
  public Optional<PermissionDefinition> findByCode(String code) {
    List<PermissionDefinition> rows =
        jdbc.query(
            """
                select permission_code, module_code, permission_name, description, risk_level,
                       dependencies_json, sort_order, enabled
                  from iam.permission_definition
                 where permission_code = ?
                """,
            ROW_MAPPER,
            code);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  @Override
  public Set<String> codesForRole(String tenantId, String roleCode) {
    return jdbc.query(
            """
                select distinct pd.permission_code
                  from iam.permission_definition pd
                  join iam.role_permission rp on rp.permission_code = pd.permission_code
                 where rp.tenant_id = ? and rp.role_code = ?
                   and pd.enabled = true
                """,
            (rs, rowNum) -> rs.getString(1),
            tenantId,
            roleCode)
        .stream()
        .collect(Collectors.toCollection(HashSet::new));
  }

  private static final org.springframework.jdbc.core.RowMapper<PermissionDefinition> ROW_MAPPER =
      (rs, rowNum) ->
          new PermissionDefinition(
              rs.getString("permission_code"),
              rs.getString("module_code"),
              rs.getString("permission_name"),
              rs.getString("description"),
              PermissionRisk.from(rs.getString("risk_level")),
              parseDependencyCodes(rs.getString("dependencies_json")),
              rs.getInt("sort_order"),
              rs.getBoolean("enabled"));

  @SuppressWarnings("unchecked")
  private static Set<String> parseDependencyCodes(String json) {
    if (json == null || json.isBlank()) {
      return Set.of();
    }
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper =
          new com.fasterxml.jackson.databind.ObjectMapper();
      List<String> values = mapper.readValue(json, List.class);
      return new HashSet<>(values);
    } catch (Exception ex) {
      return Set.of();
    }
  }
}