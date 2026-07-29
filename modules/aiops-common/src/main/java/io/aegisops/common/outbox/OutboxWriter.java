package io.aegisops.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
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

  private static final String REPLAYABLE_ENQUEUE_SQL =
      """
      insert into automation_outbox(
        id, tenant_id, target_app, job_name, payload, status, retry_count, max_retries,
        available_at, idempotency_key, replay_requested, created_at, updated_at)
      values (?, ?, ?, ?, ?::jsonb, 'pending', 0, ?, ?, ?, false, now(), now())
      on conflict (target_app, job_name, idempotency_key)
      where idempotency_key is not null
      do update set
        payload = case
          when automation_outbox.status in ('processing', 'failed') then excluded.payload
          else automation_outbox.payload
        end,
        max_retries = case
          when automation_outbox.status in ('processing', 'failed') then excluded.max_retries
          else automation_outbox.max_retries
        end,
        available_at = case
          when automation_outbox.status in ('processing', 'failed') then excluded.available_at
          else automation_outbox.available_at
        end,
        status = case
          when automation_outbox.status = 'failed' then 'pending'
          else automation_outbox.status
        end,
        retry_count = case
          when automation_outbox.status = 'failed' then 0
          else automation_outbox.retry_count
        end,
        lease_until = case
          when automation_outbox.status = 'failed' then null
          else automation_outbox.lease_until
        end,
        claim_token = case
          when automation_outbox.status = 'failed' then null
          else automation_outbox.claim_token
        end,
        error_message = case
          when automation_outbox.status = 'failed' then null
          else automation_outbox.error_message
        end,
        processed_at = case
          when automation_outbox.status = 'failed' then null
          else automation_outbox.processed_at
        end,
        replay_requested = case
          when automation_outbox.status = 'processing' then true
          when automation_outbox.status = 'failed' then false
          else automation_outbox.replay_requested
        end,
        updated_at = case
          when automation_outbox.status in ('processing', 'failed') then now()
          else automation_outbox.updated_at
        end
      where automation_outbox.tenant_id is not distinct from excluded.tenant_id
      returning automation_outbox.id
      """;

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public OutboxWriter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Transactional(propagation = Propagation.REQUIRED)
  public String enqueue(String targetApp, String jobName, Map<String, Object> payload) {
    String tenantId = resolveTenantId(payload);
    return enqueue(new OutboxMessage(tenantId, targetApp, jobName, payload, null, 3, null));
  }

  @Transactional(propagation = Propagation.REQUIRED)
  public String enqueue(
      String targetApp, String jobName, String tenantId, Map<String, Object> payload) {
    return enqueue(new OutboxMessage(tenantId, targetApp, jobName, payload, null, 3, null));
  }

  @Transactional(propagation = Propagation.REQUIRED)
  public String enqueue(OutboxMessage message) {
    String id = "outbox_" + UUID.randomUUID().toString().replace("-", "");
    String payloadJson = serialize(message.payload());
    int inserted =
        jdbc.update(
            """
        insert into automation_outbox(id, tenant_id, target_app, job_name, payload, status,
                                      retry_count, max_retries, available_at, idempotency_key,
                                      created_at, updated_at)
        values (?, ?, ?, ?, ?::jsonb, 'pending', 0, ?, ?, ?, now(), now())
        on conflict (target_app, job_name, idempotency_key)
        where idempotency_key is not null
        do nothing
        """,
            id,
            message.tenantId(),
            message.targetApp(),
            message.jobName(),
            payloadJson,
            message.maxRetries(),
            message.availableAt(),
            message.idempotencyKey());
    if (inserted == 1) {
      return id;
    }
    if (message.idempotencyKey() == null) {
      throw new IllegalStateException("outbox insert did not create a row");
    }
    return existingId(message);
  }

  /**
   * Enqueue {@code message}, returning an exhausted row with the same idempotency key to the queue.
   *
   * <p>This is an explicit opt-in for event producers that can safely replay a failed delivery.
   * Ordinary {@link #enqueue(OutboxMessage)} calls retain terminal outbox rows unchanged.
   */
  @Transactional(propagation = Propagation.REQUIRED)
  public String enqueueOrRequeueFailed(OutboxMessage message) {
    if (message.idempotencyKey() == null) {
      return enqueue(message);
    }
    try {
      String id =
          jdbc.queryForObject(
              REPLAYABLE_ENQUEUE_SQL,
              String.class,
              "outbox_" + UUID.randomUUID().toString().replace("-", ""),
              message.tenantId(),
              message.targetApp(),
              message.jobName(),
              serialize(message.payload()),
              message.maxRetries(),
              message.availableAt(),
              message.idempotencyKey());
      if (id == null) {
        throw tenantCollision();
      }
      return id;
    } catch (EmptyResultDataAccessException exception) {
      throw tenantCollision();
    }
  }

  private String existingId(OutboxMessage message) {
    try {
      return jdbc.queryForObject(
          """
          select id from automation_outbox
           where target_app = ? and job_name = ? and idempotency_key = ?
             and tenant_id is not distinct from ?
          """,
          String.class,
          message.targetApp(),
          message.jobName(),
          message.idempotencyKey(),
          message.tenantId());
    } catch (EmptyResultDataAccessException exception) {
      throw tenantCollision();
    }
  }

  private IllegalStateException tenantCollision() {
    return new IllegalStateException("outbox idempotency key belongs to another tenant");
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
