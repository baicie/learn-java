package io.aegisops.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public AuditRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(AuditEvent event) {
    jdbc.update(
        """
        insert into public.audit_log(
            id,
            tenant_id,
            actor_user_id,
            action,
            target_type,
            target_id,
            before_json,
            after_json,
            detail_json,
            created_at
        )
        values (
            :id,
            :tenantId,
            :actorId,
            :action,
            :resourceType,
            :resourceId,
            cast(:beforeJson as jsonb),
            cast(:afterJson as jsonb),
            cast(:detailJson as jsonb),
            :createdAt
        )
        """,
        Map.of(
            "id", event.id(),
            "tenantId", event.tenantId(),
            "actorId", event.actorId(),
            "action", event.action(),
            "resourceType", event.resourceType(),
            "resourceId", event.resourceId(),
            "beforeJson", event.beforeJson(),
            "afterJson", event.afterJson(),
            "detailJson", event.detailJson(),
            "createdAt", event.createdAt()));
  }

  public List<AuditEvent> listRecent(String tenantId, int limit) {
    return jdbc.query(
        """
        select
            id,
            tenant_id,
            actor_user_id,
            action,
            target_type,
            target_id,
            before_json::text,
            after_json::text,
            detail_json::text,
            created_at
        from public.audit_log
        where tenant_id = :tenantId
        order by created_at desc, id desc
        limit :limit
        """,
        Map.of("tenantId", tenantId, "limit", limit),
        this::map);
  }

  public List<AuditEvent> listByResource(
      String tenantId, String resourceType, String resourceId, int limit) {
    return jdbc.query(
        """
        select
            id,
            tenant_id,
            actor_user_id,
            action,
            target_type,
            target_id,
            before_json::text,
            after_json::text,
            detail_json::text,
            created_at
        from public.audit_log
        where tenant_id = :tenantId
          and target_type = :resourceType
          and target_id = :resourceId
        order by created_at desc, id desc
        limit :limit
        """,
        Map.of(
            "tenantId", tenantId,
            "resourceType", resourceType,
            "resourceId", resourceId,
            "limit", limit),
        this::map);
  }

  private AuditEvent map(ResultSet rs, int rowNumber) throws SQLException {
    return new AuditEvent(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("actor_user_id"),
        rs.getString("action"),
        rs.getString("target_type"),
        rs.getString("target_id"),
        rs.getString("before_json"),
        rs.getString("after_json"),
        rs.getString("detail_json"),
        rs.getObject("created_at", OffsetDateTime.class));
  }
}
