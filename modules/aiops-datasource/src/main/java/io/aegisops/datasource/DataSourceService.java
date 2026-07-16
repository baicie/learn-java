package io.aegisops.datasource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.common.outbox.OutboxMessage;
import io.aegisops.common.outbox.OutboxWriter;
import io.aegisops.datasource.api.dto.StartSyncResponse;
import io.aegisops.zabbix.ZabbixClient;
import io.aegisops.zabbix.ZabbixClientFactory;
import io.aegisops.zabbix.ZabbixConfig;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataSourceService {
  private static final String SOURCE_ZABBIX = "zabbix";

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final ZabbixClientFactory zabbixClientFactory;
  private final OutboxWriter outboxWriter;

  public DataSourceService(
      JdbcTemplate jdbc,
      ObjectMapper objectMapper,
      ZabbixClientFactory zabbixClientFactory,
      OutboxWriter outboxWriter) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.zabbixClientFactory = zabbixClientFactory;
    this.outboxWriter = outboxWriter;
  }

  @Transactional
  public StartSyncResponse startSync(String tenantId, String id) {
    DataSourceEntity entity = getEntity(tenantId, id);
    ensureZabbix(entity);
    String runId = newId("sync");
    jdbc.update(
        """
        insert into datasource_sync_run(id, tenant_id, datasource_id, sync_type, status, started_at)
        values (?, ?, ?, 'manual', 'pending', now())
        """,
        runId,
        tenantId,
        id);
    Map<String, Object> payload = Map.of("tenantId", tenantId, "datasourceId", id, "runId", runId);
    outboxWriter.enqueue(
        new OutboxMessage(
            tenantId,
            "worker",
            "zabbix-sync",
            payload,
            "zabbix-sync:" + tenantId + ":" + id + ":" + runId,
            3,
            null));
    return new StartSyncResponse(runId, "pending");
  }

  public List<DataSourceRecord> list(String tenantId) {
    return jdbc.query(
        """
                select id, tenant_id, type, name, config_json->>'endpoint' as endpoint,
                       status, created_at, updated_at, last_sync_at
                from datasource where tenant_id = ? order by created_at desc
                """,
        (rs, rowNum) ->
            new DataSourceRecord(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("type"),
                rs.getString("name"),
                rs.getString("endpoint"),
                rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("last_sync_at", OffsetDateTime.class)),
        tenantId);
  }

  public DataSourceRecord create(String tenantId, CreateDataSourceRequest request) {
    String type = normalizeType(request.type());
    if (!SOURCE_ZABBIX.equals(type)) {
      throw new AppException(
          "UNSUPPORTED_DATASOURCE", "Only zabbix datasource is supported in Phase1");
    }

    ZabbixConfig config = toZabbixConfig(request.zabbix());
    String id = newId("ds");
    String configJson = writeJson(config);

    jdbc.update(
        """
                insert into datasource(id, tenant_id, type, name, status, config_json, created_at, updated_at)
                values (?, ?, ?, ?, 'inactive', ?::jsonb, now(), now())
                """,
        id,
        tenantId,
        type,
        request.name().trim(),
        configJson);
    return getRecord(tenantId, id);
  }

  public TestDataSourceResponse test(String tenantId, String id) {
    DataSourceEntity entity = getEntity(tenantId, id);
    ensureZabbix(entity);
    try {
      String version = zabbixClient(entity).testConnection();
      jdbc.update(
          "update datasource set status = 'active', updated_at = now() where tenant_id = ? and id = ?",
          tenantId,
          id);
      return new TestDataSourceResponse(true, "Zabbix connection succeeded", version);
    } catch (RuntimeException ex) {
      jdbc.update(
          "update datasource set status = 'error', updated_at = now() where tenant_id = ? and id = ?",
          tenantId,
          id);
      return new TestDataSourceResponse(false, ex.getMessage(), null);
    }
  }

  public List<SyncRunRecord> syncRuns(String tenantId, String datasourceId) {
    ensureExists(tenantId, datasourceId);
    return jdbc.query(
        """
                select id, datasource_id, sync_type, status, message, stats_json::text, started_at, finished_at
                from datasource_sync_run
                where tenant_id = ? and datasource_id = ?
                order by started_at desc
                limit 20
                """,
        (rs, rowNum) ->
            new SyncRunRecord(
                rs.getString("id"),
                rs.getString("datasource_id"),
                rs.getString("sync_type"),
                rs.getString("status"),
                rs.getString("message"),
                rs.getString("stats_json"),
                rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("finished_at", OffsetDateTime.class)),
        tenantId,
        datasourceId);
  }

  private DataSourceRecord getRecord(String tenantId, String id) {
    return jdbc.queryForObject(
        """
                select id, tenant_id, type, name, config_json->>'endpoint' as endpoint,
                       status, created_at, updated_at, last_sync_at
                from datasource where tenant_id = ? and id = ?
                """,
        (rs, rowNum) ->
            new DataSourceRecord(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("type"),
                rs.getString("name"),
                rs.getString("endpoint"),
                rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("last_sync_at", OffsetDateTime.class)),
        tenantId,
        id);
  }

  private DataSourceEntity getEntity(String tenantId, String id) {
    try {
      return jdbc.queryForObject(
          """
                    select id, tenant_id, type, name, status, config_json::text, created_at, updated_at, last_sync_at
                    from datasource where tenant_id = ? and id = ?
                    """,
          (rs, rowNum) ->
              new DataSourceEntity(
                  rs.getString("id"),
                  rs.getString("tenant_id"),
                  rs.getString("type"),
                  rs.getString("name"),
                  rs.getString("status"),
                  rs.getString("config_json"),
                  rs.getObject("created_at", OffsetDateTime.class),
                  rs.getObject("updated_at", OffsetDateTime.class),
                  rs.getObject("last_sync_at", OffsetDateTime.class)),
          tenantId,
          id);
    } catch (EmptyResultDataAccessException ex) {
      throw new AppException("DATASOURCE_NOT_FOUND", "Datasource not found");
    }
  }

  private void ensureExists(String tenantId, String datasourceId) {
    getEntity(tenantId, datasourceId);
  }

  private void ensureZabbix(DataSourceEntity entity) {
    if (!SOURCE_ZABBIX.equals(entity.type())) {
      throw new AppException(
          "UNSUPPORTED_DATASOURCE", "Only zabbix datasource can be tested or synced in Phase1");
    }
  }

  private ZabbixClient zabbixClient(DataSourceEntity entity) {
    return zabbixClientFactory.create(readZabbixConfig(entity.configJson()));
  }

  private ZabbixConfig toZabbixConfig(ZabbixConfigRequest request) {
    ZabbixConfig config =
        new ZabbixConfig(
            trimToNull(request.endpoint()),
            trimToNull(request.username()),
            trimToNull(request.password()),
            trimToNull(request.apiToken()),
            request.connectTimeoutSeconds(),
            request.readTimeoutSeconds());

    validateZabbixConfig(config);
    return config;
  }

  private void validateZabbixConfig(ZabbixConfig config) {
    if (config.endpoint() == null || config.endpoint().isBlank()) {
      throw new AppException("DATASOURCE_CONFIG_INVALID", "Zabbix endpoint is required");
    }

    if (!config.hasAuthentication()) {
      throw new AppException(
          "DATASOURCE_CONFIG_INVALID", "Zabbix username/password or apiToken is required");
    }
  }

  private ZabbixConfig readZabbixConfig(String configJson) {
    try {
      ZabbixConfig config = objectMapper.readValue(configJson, ZabbixConfig.class);
      validateZabbixConfig(config);
      return config;
    } catch (AppException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new AppException("DATASOURCE_CONFIG_INVALID", "Datasource config is invalid");
    }
  }

  private String normalizeType(String type) {
    return type == null ? "" : type.trim().toLowerCase();
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new AppException("JSON_SERIALIZE_FAILED", "Failed to serialize JSON");
    }
  }

  private String trimToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
