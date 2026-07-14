package io.aegisops.platform.iam.repository;

import io.aegisops.platform.iam.domain.PlatformUser;
import io.aegisops.platform.iam.domain.PlatformUserPage;
import io.aegisops.platform.iam.domain.PlatformUserQuery;
import io.aegisops.platform.iam.domain.PlatformUserStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPlatformUserRepository implements PlatformUserRepository {

  private final JdbcTemplate jdbc;

  public JdbcPlatformUserRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public PlatformUserPage search(PlatformUserQuery query) {
    PlatformUserQuery q = query.normalized();
    StringBuilder where = new StringBuilder(" where u.tenant_id = ? and u.deleted_at is null ");
    List<Object> params = new ArrayList<>();
    params.add(currentTenant());
    if (q.keyword() != null && !q.keyword().isBlank()) {
      where.append(" and (lower(u.username) like ? or lower(u.display_name) like ?) ");
      String kw = "%" + q.keyword().toLowerCase() + "%";
      params.add(kw);
      params.add(kw);
    }
    if (q.status() != null) {
      where.append(" and u.status = ? ");
      params.add(q.status().value());
    }
    if (q.roleCodes() != null && !q.roleCodes().isEmpty()) {
      String placeholders = q.roleCodes().stream().map(c -> "?").collect(Collectors.joining(","));
      where.append(
          " and exists (select 1 from iam.user_role ur where ur.user_id = u.id and ur.role_code in ("
              + placeholders
              + ")) ");
      params.addAll(q.roleCodes());
    }
    String baseSql =
        """
            select u.id, u.tenant_id, u.username, u.display_name, u.email, u.status,
                   u.last_login_at, u.created_at, u.updated_at, u.row_version
            from sys_user u
            """
            + where
            + " order by u.created_at desc, u.id desc limit ? offset ? ";
    List<Object> pageParams = new ArrayList<>(params);
    pageParams.add(q.pageSize());
    pageParams.add((long) (q.page() - 1) * q.pageSize());
    List<UserRow> rows = jdbc.query(baseSql, USER_MAPPER, pageParams.toArray());
    List<PlatformUser> items = new ArrayList<>(rows.size());
    for (UserRow row : rows) {
      items.add(toDomain(row, listRoleRefs(row.id())));
    }
    String countSql = " select count(*) from sys_user u " + where;
    Long total = jdbc.queryForObject(countSql, Long.class, params.toArray());
    return new PlatformUserPage(items, total == null ? 0L : total, q.page(), q.pageSize());
  }

  @Override
  public Optional<PlatformUser> findById(String id) {
    List<UserRow> rows =
        jdbc.query(
            """
                select u.id, u.tenant_id, u.username, u.display_name, u.email, u.status,
                       u.last_login_at, u.created_at, u.updated_at, u.row_version
                from sys_user u
                where u.id = ? and u.tenant_id = ? and u.deleted_at is null
                """,
            USER_MAPPER,
            id,
            currentTenant());
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    UserRow row = rows.get(0);
    return Optional.of(toDomain(row, listRoleRefs(row.id())));
  }

  @Override
  public boolean existsByUsername(String tenantId, String username) {
    Integer count =
        jdbc.queryForObject(
            " select count(*) from sys_user where tenant_id = ? and lower(username) = lower(?) and deleted_at is null ",
            Integer.class,
            tenantId == null ? currentTenant() : tenantId,
            username);
    return count != null && count > 0;
  }

  @Override
  public Optional<PlatformUser> insert(PlatformUserCreateCommand command) {
    int updated =
        jdbc.update(
            """
                insert into sys_user(
                    id, tenant_id, username, display_name, email, password_hash,
                    status, failed_login_count, row_version, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, 0, 1, ?, ?)
                on conflict (id) do nothing
                """,
            command.id(),
            command.tenantId() == null ? currentTenant() : command.tenantId(),
            command.username(),
            command.displayName(),
            command.email(),
            command.passwordHash(),
            command.status().value(),
            command.now(),
            command.now());
    if (updated == 0) {
      return Optional.empty();
    }
    return findById(command.id());
  }

  @Override
  public void replaceRoles(
      String tenantId, String userId, List<String> roleCodes, OffsetDateTime now) {
    jdbc.update(" delete from iam.user_role where user_id = ? ", userId);
    if (roleCodes == null || roleCodes.isEmpty()) {
      return;
    }
    for (String role : roleCodes) {
      jdbc.update(
          """
              insert into iam.user_role(tenant_id, user_id, role_code, granted_at, granted_by)
              values (?, ?, ?, ?, ?)
              on conflict (user_id, role_code) do nothing
              """,
          tenantId == null ? currentTenant() : tenantId,
          userId,
          role,
          now,
          "system");
    }
  }

  @Override
  public void update(String userId, String displayName, String email, OffsetDateTime now) {
    jdbc.update(
        """
            update sys_user
               set display_name = coalesce(?, display_name),
                   email        = coalesce(?, email),
                   updated_at   = ?,
                   row_version  = row_version + 1
             where id = ?
               and tenant_id = ?
               and deleted_at is null
            """,
        displayName,
        email,
        now,
        userId,
        currentTenant());
  }

  @Override
  public int updateStatus(
      String userId,
      PlatformUserStatus status,
      OffsetDateTime lockedUntil,
      OffsetDateTime now,
      int expectedVersion) {
    return jdbc.update(
        """
            update sys_user
               set status = ?,
                   locked_until = ?,
                   updated_at = ?,
                   row_version = row_version + 1
             where id = ?
               and tenant_id = ?
               and deleted_at is null
               and row_version = ?
            """,
        status.value(),
        lockedUntil,
        now,
        userId,
        currentTenant(),
        expectedVersion);
  }

  @Override
  public void recordLogin(
      String userId, OffsetDateTime lastLoginAt, int success, OffsetDateTime now) {
    if (success == 1) {
      jdbc.update(
          """
              update sys_user
                 set last_login_at = ?,
                     failed_login_count = 0,
                     locked_until = null,
                     updated_at = ?
               where id = ?
              """,
          lastLoginAt,
          now,
          userId);
    } else {
      jdbc.update(
          """
              update sys_user
                 set failed_login_count = failed_login_count + 1,
                     locked_until = case
                         when failed_login_count + 1 >= 5 then (?::timestamptz + interval '15 minutes')
                         else locked_until
                     end,
                     updated_at = ?
               where id = ?
              """,
          now,
          now,
          userId);
    }
  }

  @Override
  public int deleteRolesForRole(String tenantId, String roleCode) {
    return jdbc.update(
        " delete from iam.user_role where tenant_id = ? and role_code = ? ",
        tenantId == null ? currentTenant() : tenantId,
        roleCode);
  }

  private List<PlatformUser.RoleRef> listRoleRefs(String userId) {
    return jdbc.query(
        """
            select r.code, r.name, ur.role_code
              from iam.user_role ur
              join iam.role_definition r on r.code = ur.role_code and r.tenant_id = ur.tenant_id
             where ur.user_id = ?
            """,
        (rs, rowNum) -> new PlatformUser.RoleRef(rs.getString("code"), rs.getString("name")),
        userId);
  }

  private PlatformUser toDomain(UserRow row, List<PlatformUser.RoleRef> roles) {
    return new PlatformUser(
        row.id,
        row.tenantId,
        row.username,
        row.displayName,
        row.email,
        PlatformUserStatus.from(row.status),
        roles,
        java.util.Map.of(),
        row.lastLoginAt,
        row.createdAt,
        row.updatedAt,
        row.rowVersion);
  }

  /** TenantResolutionFacade placeholder - centralized through TenantContextService beans. */
  private String currentTenant() {
    return io.aegisops.platform.iam.repository.SecurityContextSupport.currentTenant();
  }

  private static final RowMapper<UserRow> USER_MAPPER =
      new RowMapper<>() {
        @Override
        public UserRow mapRow(ResultSet rs, int rowNum) throws SQLException {
          return new UserRow(
              rs.getString("id"),
              rs.getString("tenant_id"),
              rs.getString("username"),
              rs.getString("display_name"),
              rs.getString("email"),
              rs.getString("status"),
              rs.getObject("last_login_at", OffsetDateTime.class),
              rs.getObject("created_at", OffsetDateTime.class),
              rs.getObject("updated_at", OffsetDateTime.class),
              rs.getInt("row_version"));
        }
      };

  private record UserRow(
      String id,
      String tenantId,
      String username,
      String displayName,
      String email,
      String status,
      OffsetDateTime lastLoginAt,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      int rowVersion) {}
}
