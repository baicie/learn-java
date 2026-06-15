package io.aegisops.user;

import io.aegisops.common.id.Ids;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
  private final JdbcTemplate jdbc;

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
              Set.of(),
              rs.getObject("created_at", OffsetDateTime.class),
              rs.getObject("updated_at", OffsetDateTime.class));

  public UserRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
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
    return rows.stream().findFirst().map(this::withRoles);
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
    return rows.stream().findFirst().map(this::withRoles);
  }

  public List<UserAccount> findAllByTenantId(String tenantId) {
    return jdbc
        .query(
            """
                select id, tenant_id, username, display_name, email, password_hash, status, created_at, updated_at
                from sys_user where tenant_id = ? order by created_at desc
                """,
            userMapper,
            tenantId)
        .stream()
        .map(this::withRoles)
        .toList();
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

  public void ensureRole(String code, String name) {
    Integer count =
        jdbc.queryForObject("select count(*) from sys_role where code = ?", Integer.class, code);
    if (count == null || count == 0) {
      jdbc.update("insert into sys_role(id, code, name) values (?, ?, ?)", Ids.newId(), code, name);
    }
  }

  public void attachRole(String userId, String roleCode) {
    String roleId =
        jdbc.queryForObject("select id from sys_role where code = ?", String.class, roleCode);
    Integer count =
        jdbc.queryForObject(
            "select count(*) from sys_user_role where user_id = ? and role_id = ?",
            Integer.class,
            userId,
            roleId);
    if (count == null || count == 0) {
      jdbc.update("insert into sys_user_role(user_id, role_id) values (?, ?)", userId, roleId);
    }
  }

  private UserAccount withRoles(UserAccount user) {
    List<String> roles =
        jdbc.queryForList(
            """
                select r.code from sys_role r
                join sys_user_role ur on ur.role_id = r.id
                where ur.user_id = ?
                order by r.code
                """,
            String.class,
            user.id());
    return new UserAccount(
        user.id(),
        user.tenantId(),
        user.username(),
        user.displayName(),
        user.email(),
        user.passwordHash(),
        user.status(),
        new LinkedHashSet<>(roles),
        user.createdAt(),
        user.updatedAt());
  }
}
