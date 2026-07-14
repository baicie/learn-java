package io.aegisops.workrecord.infrastructure.jdbc;

import io.aegisops.workrecord.application.port.NotificationRepository;
import io.aegisops.workrecord.domain.model.WorkRecordNotification;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcNotificationRepository implements NotificationRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcNotificationRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean insertIfAbsent(WorkRecordNotification value) {
    Map<String, Object> params = new HashMap<>();
    params.put("id", value.id());
    params.put("tenantId", value.tenantId());
    params.put("userId", value.userId());
    params.put("type", value.notificationType());
    params.put("title", value.title());
    params.put("content", value.content());
    params.put("resourceType", value.resourceType());
    params.put("resourceId", value.resourceId());
    params.put("dedupeKey", value.dedupeKey());
    return jdbc.update(
            """
        insert into work_record.wr_notification(
          id, tenant_id, user_id, notification_type, title, content,
          resource_type, resource_id, dedupe_key)
        values (:id, :tenantId, :userId, :type, :title, :content,
                :resourceType, :resourceId, :dedupeKey)
        on conflict (tenant_id, user_id, dedupe_key) do nothing
        """,
            params)
        == 1;
  }

  @Override
  public List<WorkRecordNotification> listUnread(String tenantId, String userId, int limit) {
    return jdbc.query(
        """
        select id, tenant_id, user_id, notification_type, title, content,
               resource_type, resource_id, dedupe_key, created_at, read_at
          from work_record.wr_notification
         where tenant_id = :tenantId and user_id = :userId and read_at is null
         order by created_at desc, id desc limit :limit
        """,
        Map.of("tenantId", tenantId, "userId", userId, "limit", limit),
        (rs, rowNum) ->
            new WorkRecordNotification(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("user_id"),
                rs.getString("notification_type"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("resource_type"),
                rs.getString("resource_id"),
                rs.getString("dedupe_key"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("read_at", OffsetDateTime.class)));
  }

  @Override
  public boolean markRead(String tenantId, String userId, String notificationId) {
    return jdbc.update(
            """
        update work_record.wr_notification set read_at = now()
         where tenant_id = :tenantId and user_id = :userId and id = :id and read_at is null
        """,
            Map.of("tenantId", tenantId, "userId", userId, "id", notificationId))
        == 1;
  }
}
