package io.aegisops.execution;

import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.Tables.WEBHOOK_CONNECTOR;
import static io.aegisops.persistence.jooq.Tables.WEBHOOK_EXECUTION_POLICY;

import io.aegisops.execution.dto.WebhookConnectorCreateCommand;
import io.aegisops.execution.dto.WebhookConnectorRecord;
import io.aegisops.execution.dto.WebhookPolicyCreateCommand;
import io.aegisops.execution.dto.WebhookPolicyRecord;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqWebhookRepository implements WebhookRepository {
  private final DSLContext dsl;

  public JooqWebhookRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void createConnector(WebhookConnectorCreateCommand command) {
    dsl.insertInto(WEBHOOK_CONNECTOR)
        .set(WEBHOOK_CONNECTOR.ID, command.id())
        .set(WEBHOOK_CONNECTOR.TENANT_ID, command.tenantId())
        .set(WEBHOOK_CONNECTOR.NAME, command.name())
        .set(WEBHOOK_CONNECTOR.DESCRIPTION, command.description())
        .set(WEBHOOK_CONNECTOR.BASE_URL, command.baseUrl())
        .set(WEBHOOK_CONNECTOR.DEFAULT_METHOD, command.defaultMethod())
        .set(WEBHOOK_CONNECTOR.DEFAULT_HEADERS, jsonbValue(command.defaultHeadersJson()))
        .set(WEBHOOK_CONNECTOR.SENSITIVE_HEADERS, jsonbValue(command.sensitiveHeadersJson()))
        .set(WEBHOOK_CONNECTOR.ENABLED, command.enabled())
        .set(WEBHOOK_CONNECTOR.CREATED_BY, command.createdBy())
        .set(WEBHOOK_CONNECTOR.CREATED_AT, DSL.currentOffsetDateTime())
        .set(WEBHOOK_CONNECTOR.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void createPolicy(WebhookPolicyCreateCommand command) {
    dsl.insertInto(WEBHOOK_EXECUTION_POLICY)
        .set(WEBHOOK_EXECUTION_POLICY.ID, command.id())
        .set(WEBHOOK_EXECUTION_POLICY.TENANT_ID, command.tenantId())
        .set(WEBHOOK_EXECUTION_POLICY.CONNECTOR_ID, command.connectorId())
        .set(WEBHOOK_EXECUTION_POLICY.ALLOW_LIVE, command.allowLive())
        .set(WEBHOOK_EXECUTION_POLICY.ALLOWED_HOSTS, jsonbValue(command.allowedHostsJson()))
        .set(WEBHOOK_EXECUTION_POLICY.ALLOWED_METHODS, jsonbValue(command.allowedMethodsJson()))
        .set(WEBHOOK_EXECUTION_POLICY.BLOCK_PRIVATE_IP, command.blockPrivateIp())
        .set(WEBHOOK_EXECUTION_POLICY.BLOCK_LOCALHOST, command.blockLocalhost())
        .set(WEBHOOK_EXECUTION_POLICY.BLOCK_METADATA_IP, command.blockMetadataIp())
        .set(WEBHOOK_EXECUTION_POLICY.MAX_BODY_BYTES, command.maxBodyBytes())
        .set(WEBHOOK_EXECUTION_POLICY.TIMEOUT_MILLIS, command.timeoutMillis())
        .set(WEBHOOK_EXECUTION_POLICY.ENABLED, command.enabled())
        .set(WEBHOOK_EXECUTION_POLICY.CREATED_AT, DSL.currentOffsetDateTime())
        .set(WEBHOOK_EXECUTION_POLICY.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public List<WebhookConnectorRecord> listConnectors(String tenantId, boolean includeDisabled) {
    var condition = WEBHOOK_CONNECTOR.TENANT_ID.eq(tenantId);
    if (!includeDisabled) {
      condition = condition.and(WEBHOOK_CONNECTOR.ENABLED.isTrue());
    }

    return dsl.select(
            WEBHOOK_CONNECTOR.ID,
            WEBHOOK_CONNECTOR.TENANT_ID,
            WEBHOOK_CONNECTOR.NAME,
            WEBHOOK_CONNECTOR.DESCRIPTION,
            WEBHOOK_CONNECTOR.BASE_URL,
            WEBHOOK_CONNECTOR.DEFAULT_METHOD,
            WEBHOOK_CONNECTOR.DEFAULT_HEADERS.cast(String.class).as("default_headers_json"),
            WEBHOOK_CONNECTOR.SENSITIVE_HEADERS.cast(String.class).as("sensitive_headers_json"),
            WEBHOOK_CONNECTOR.ENABLED,
            WEBHOOK_CONNECTOR.CREATED_BY,
            WEBHOOK_CONNECTOR.CREATED_AT,
            WEBHOOK_CONNECTOR.UPDATED_AT)
        .from(WEBHOOK_CONNECTOR)
        .where(condition)
        .orderBy(WEBHOOK_CONNECTOR.CREATED_AT.desc())
        .fetch(this::toConnectorRecord);
  }

  @Override
  public Optional<WebhookConnectorRecord> findConnector(String tenantId, String connectorId) {
    return dsl.select(
            WEBHOOK_CONNECTOR.ID,
            WEBHOOK_CONNECTOR.TENANT_ID,
            WEBHOOK_CONNECTOR.NAME,
            WEBHOOK_CONNECTOR.DESCRIPTION,
            WEBHOOK_CONNECTOR.BASE_URL,
            WEBHOOK_CONNECTOR.DEFAULT_METHOD,
            WEBHOOK_CONNECTOR.DEFAULT_HEADERS.cast(String.class).as("default_headers_json"),
            WEBHOOK_CONNECTOR.SENSITIVE_HEADERS.cast(String.class).as("sensitive_headers_json"),
            WEBHOOK_CONNECTOR.ENABLED,
            WEBHOOK_CONNECTOR.CREATED_BY,
            WEBHOOK_CONNECTOR.CREATED_AT,
            WEBHOOK_CONNECTOR.UPDATED_AT)
        .from(WEBHOOK_CONNECTOR)
        .where(WEBHOOK_CONNECTOR.TENANT_ID.eq(tenantId))
        .and(WEBHOOK_CONNECTOR.ID.eq(connectorId))
        .fetchOptional(this::toConnectorRecord);
  }

  @Override
  public Optional<WebhookPolicyRecord> findPolicy(String tenantId, String connectorId) {
    return dsl.select(
            WEBHOOK_EXECUTION_POLICY.ID,
            WEBHOOK_EXECUTION_POLICY.TENANT_ID,
            WEBHOOK_EXECUTION_POLICY.CONNECTOR_ID,
            WEBHOOK_EXECUTION_POLICY.ALLOW_LIVE,
            WEBHOOK_EXECUTION_POLICY.ALLOWED_HOSTS.cast(String.class).as("allowed_hosts_json"),
            WEBHOOK_EXECUTION_POLICY.ALLOWED_METHODS.cast(String.class).as("allowed_methods_json"),
            WEBHOOK_EXECUTION_POLICY.BLOCK_PRIVATE_IP,
            WEBHOOK_EXECUTION_POLICY.BLOCK_LOCALHOST,
            WEBHOOK_EXECUTION_POLICY.BLOCK_METADATA_IP,
            WEBHOOK_EXECUTION_POLICY.MAX_BODY_BYTES,
            WEBHOOK_EXECUTION_POLICY.TIMEOUT_MILLIS,
            WEBHOOK_EXECUTION_POLICY.ENABLED,
            WEBHOOK_EXECUTION_POLICY.CREATED_AT,
            WEBHOOK_EXECUTION_POLICY.UPDATED_AT)
        .from(WEBHOOK_EXECUTION_POLICY)
        .where(WEBHOOK_EXECUTION_POLICY.TENANT_ID.eq(tenantId))
        .and(WEBHOOK_EXECUTION_POLICY.CONNECTOR_ID.eq(connectorId))
        .fetchOptional(this::toPolicyRecord);
  }

  @Override
  public boolean setConnectorEnabled(String tenantId, String connectorId, boolean enabled) {
    return dsl.update(WEBHOOK_CONNECTOR)
            .set(WEBHOOK_CONNECTOR.ENABLED, enabled)
            .set(WEBHOOK_CONNECTOR.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(WEBHOOK_CONNECTOR.TENANT_ID.eq(tenantId))
            .and(WEBHOOK_CONNECTOR.ID.eq(connectorId))
            .execute()
        > 0;
  }

  private WebhookConnectorRecord toConnectorRecord(org.jooq.Record record) {
    return new WebhookConnectorRecord(
        record.get(WEBHOOK_CONNECTOR.ID),
        record.get(WEBHOOK_CONNECTOR.TENANT_ID),
        record.get(WEBHOOK_CONNECTOR.NAME),
        record.get(WEBHOOK_CONNECTOR.DESCRIPTION),
        record.get(WEBHOOK_CONNECTOR.BASE_URL),
        record.get(WEBHOOK_CONNECTOR.DEFAULT_METHOD),
        record.get("default_headers_json", String.class),
        record.get("sensitive_headers_json", String.class),
        Boolean.TRUE.equals(record.get(WEBHOOK_CONNECTOR.ENABLED)),
        record.get(WEBHOOK_CONNECTOR.CREATED_BY),
        record.get(WEBHOOK_CONNECTOR.CREATED_AT),
        record.get(WEBHOOK_CONNECTOR.UPDATED_AT));
  }

  private WebhookPolicyRecord toPolicyRecord(org.jooq.Record record) {
    return new WebhookPolicyRecord(
        record.get(WEBHOOK_EXECUTION_POLICY.ID),
        record.get(WEBHOOK_EXECUTION_POLICY.TENANT_ID),
        record.get(WEBHOOK_EXECUTION_POLICY.CONNECTOR_ID),
        Boolean.TRUE.equals(record.get(WEBHOOK_EXECUTION_POLICY.ALLOW_LIVE)),
        record.get("allowed_hosts_json", String.class),
        record.get("allowed_methods_json", String.class),
        Boolean.TRUE.equals(record.get(WEBHOOK_EXECUTION_POLICY.BLOCK_PRIVATE_IP)),
        Boolean.TRUE.equals(record.get(WEBHOOK_EXECUTION_POLICY.BLOCK_LOCALHOST)),
        Boolean.TRUE.equals(record.get(WEBHOOK_EXECUTION_POLICY.BLOCK_METADATA_IP)),
        value(record.get(WEBHOOK_EXECUTION_POLICY.MAX_BODY_BYTES)),
        value(record.get(WEBHOOK_EXECUTION_POLICY.TIMEOUT_MILLIS)),
        Boolean.TRUE.equals(record.get(WEBHOOK_EXECUTION_POLICY.ENABLED)),
        record.get(WEBHOOK_EXECUTION_POLICY.CREATED_AT),
        record.get(WEBHOOK_EXECUTION_POLICY.UPDATED_AT));
  }

  private int value(Integer value) {
    return value == null ? 0 : value;
  }
}
