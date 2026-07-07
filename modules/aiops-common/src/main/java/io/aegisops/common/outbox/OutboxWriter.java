package io.aegisops.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cross-app dispatcher helper that appends a row to the {@code automation_outbox} table.
 *
 * <p>Any app that needs to schedule work for another app (e.g. server → worker for Zabbix sync,
 * runner → worker for postmortem generation) calls {@link #enqueue(String, String, Map)} and
 * returns immediately. The target app's {@code OutboxPoller} picks the row up.
 *
 * <p>The transaction is REQUIRED so the enqueue lands in the same transaction as the side effect
 * that produced the event (e.g. the {@code alert_event} insert). If the surrounding transaction
 * rolls back, the outbox row rolls back too — no orphan rows.
 */
@Service
public class OutboxWriter {

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public OutboxWriter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Transactional(propagation = Propagation.REQUIRED)
  public String enqueue(String targetApp, String jobName, Map<String, Object> payload) {
    String tenantId = resolveTenantId(payload);
    return enqueue(targetApp, jobName, tenantId, payload);
  }

  @Transactional(propagation = Propagation.REQUIRED)
  public String enqueue(
      String targetApp, String jobName, String tenantId, Map<String, Object> payload) {
    String id = "outbox_" + UUID.randomUUID().toString().replace("-", "");
    String payloadJson = serialize(payload);
    jdbc.update(
        """
        insert into automation_outbox(id, tenant_id, target_app, job_name, payload, status,
                                      retry_count, max_retries, created_at, updated_at)
        values (?, ?, ?, ?, ?::jsonb, 'pending', 0, 3, now(), now())
        """,
        id,
        tenantId,
        targetApp,
        jobName,
        payloadJson);
    return id;
  }

  private String resolveTenantId(Map<String, Object> payload) {
    if (payload == null) {
      return null;
    }
    Object value = payload.get("tenantId");
    if (value == null) {
      return null;
    }
    String tenantId = String.valueOf(value).trim();
    return tenantId.isBlank() ? null : tenantId;
  }

  private String serialize(Map<String, Object> payload) {
    try {
      return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize outbox payload", ex);
    }
  }
}
