package io.aegisops.user;

import io.aegisops.common.id.Ids;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
  private final JdbcTemplate jdbc;
  private final NamedParameterJdbcTemplate namedJdbc;

  private final RowMapper<UserAccount> userMapper =
      (rs, rowNum) ->
          new UserAccount(
              rs.getString("id"),
              rs.getString("tenant_id"),
              rs.getString("username"),
              rs.getString("display_name"),
              rs.getString("email"),
              rs.getString("password_hash"),
              rs.getString("status"),
              rs.getObject("created_at", OffsetDateTime.class),
              rs.getObject("updated_at", OffsetDateTime.class));

  public UserRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
    this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
  }

  public Optional<UserAccount> findByUsername(String username) {
    List<UserAccount> rows =
        jdbc.query(
            """
                select id, tenant_id, username, display_name, email, password_hash, status, created_at, updated_at
                from sys_user where username = ?
                """,
            userMapper,
            username);
    return rows.stream().findFirst();
  }

  public Optional<UserAccount> findById(String id) {
    List<UserAccount> rows =
        jdbc.query(
            """
                select id, tenant_id, username, display_name, email, password_hash, status, created_at, updated_at
                from sys_user where id = ?
                """,
            userMapper,
            id);
    return rows.stream().findFirst();
  }

  public Map<String, String> findDisplayNamesByTenantIdAndIds(
      String tenantId, Collection<String> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return Map.of();
    }

    List<String> filteredIds =
        userIds.stream()
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .toList();

    if (filteredIds.isEmpty()) {
      return Map.of();
    }

    Map<String, Object> params =
        Map.of("tenantId", tenantId, "userIds", filteredIds);

    return namedJdbc.query(
        """
        select id, username, display_name
          from sys_user
         where tenant_id = :tenantId
           and id in (:userIds)
        """,
        params,
        rs -> {
          Map<String, String> result = new LinkedHashMap<>();
          while (rs.next()) {
            String displayName = rs.getString("display_name");
            if (displayName == null || displayName.isBlank()) {
              displayName = rs.getString("username");
            }
            result.put(rs.getString("id"), displayName);
          }
          return Map.copyOf(result);
        });
  }

  public List<UserAccount> findAllByTenantId(String tenantId) {
    return jdbc.query(
        """
            select id, tenant_id, username, display_name, email, password_hash, status, created_at, updated_at
            from sys_user where tenant_id = ? order by created_at desc
            """,
        userMapper,
        tenantId);
  }

  public UserAccount create(
      String tenantId, String username, String displayName, String email, String passwordHash) {
    String id = Ids.newId();
    jdbc.update(
        """
                insert into sys_user(id, tenant_id, username, display_name, email, password_hash, status)
                values (?, ?, ?, ?, ?, ?, 'active')
                """,
        id,
        tenantId,
        username,
        displayName,
        email,
        passwordHash);
    return findById(id).orElseThrow();
  }
}
