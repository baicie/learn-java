package io.aegisops.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
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
            request_id,
            ip,
            user_agent,
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
            :requestId,
            :ip,
            :userAgent,
            :createdAt
        )
        """,
        insertParameters(event));
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
            request_id,
            ip,
            user_agent,
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
            request_id,
            ip,
            user_agent,
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
        rs.getString("request_id"),
        rs.getString("ip"),
        rs.getString("user_agent"),
        rs.getObject("created_at", OffsetDateTime.class));
  }

  private Map<String, Object> insertParameters(AuditEvent event) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("id", event.id());
    parameters.put("tenantId", event.tenantId());
    parameters.put("actorId", event.actorId());
    parameters.put("action", event.action());
    parameters.put("resourceType", event.resourceType());
    parameters.put("resourceId", event.resourceId());
    parameters.put("beforeJson", event.beforeJson());
    parameters.put("afterJson", event.afterJson());
    parameters.put("detailJson", event.detailJson());
    parameters.put("requestId", event.requestId());
    parameters.put("ip", event.ip());
    parameters.put("userAgent", event.userAgent());
    parameters.put("createdAt", event.createdAt());
    return parameters;
  }
}
