package io.aegisops.datasource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.datasource.api.dto.StartSyncResponse;
import io.aegisops.datasource.application.ManualDataSourceSyncApplicationService;
import io.aegisops.kubernetes.application.KubernetesInventoryClientFactory;
import io.aegisops.kubernetes.domain.model.KubernetesConfig;
import io.aegisops.zabbix.ZabbixClient;
import io.aegisops.zabbix.ZabbixClientFactory;
import io.aegisops.zabbix.ZabbixConfig;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataSourceService {
  private static final String SOURCE_ZABBIX = "zabbix";
  private static final String SOURCE_KUBERNETES = "kubernetes";
  private static final Set<String> PASSIVE_SOURCES =
      Set.of("opentelemetry", "rum", "github", "gitlab", "jenkins", "webhook");

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final ZabbixClientFactory zabbixClientFactory;
  private final KubernetesInventoryClientFactory kubernetesClientFactory;
  private final ManualDataSourceSyncApplicationService manualSyncService;

  public DataSourceService(
      JdbcTemplate jdbc,
      ObjectMapper objectMapper,
      ZabbixClientFactory zabbixClientFactory,
      KubernetesInventoryClientFactory kubernetesClientFactory,
      ManualDataSourceSyncApplicationService manualSyncService) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.zabbixClientFactory = zabbixClientFactory;
    this.kubernetesClientFactory = kubernetesClientFactory;
    this.manualSyncService = manualSyncService;
  }

  @Transactional
  public StartSyncResponse startSync(String tenantId, String id) {
    DataSourceEntity entity = getEntity(tenantId, id, true);
    requireActiveForManualSync(entity);
    return manualSyncService.start(tenantId, id, entity.type());
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
    Object config = toConfig(type, request);
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

  public DataSourceRecord update(String tenantId, String id, UpdateDataSourceRequest request) {
    DataSourceEntity existing = getEntity(tenantId, id);
    Object config = mergeConfig(existing, request);
    jdbc.update(
        """
        update datasource
        set config_json = ?::jsonb, name = ?, status = 'inactive', updated_at = now()
        where tenant_id = ? and id = ?
        """,
        writeJson(config),
        request.name().trim(),
        tenantId,
        id);
    return getRecord(tenantId, id);
  }

  public TestDataSourceResponse test(String tenantId, String id) {
    DataSourceEntity entity = getEntity(tenantId, id);
    try {
      String version = testConnection(entity);
      jdbc.update(
          "update datasource set status = 'active', updated_at = now() where tenant_id = ? and id = ?",
          tenantId,
          id);
      return new TestDataSourceResponse(true, testSuccessMessage(entity), version);
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
    return getEntity(tenantId, id, false);
  }

  private DataSourceEntity getEntity(String tenantId, String id, boolean forUpdate) {
    try {
      return jdbc.queryForObject(
          """
          select id, tenant_id, type, name, status, config_json::text,
                 created_at, updated_at, last_sync_at
          from datasource
          where tenant_id = ? and id = ?
          """
              + (forUpdate ? "for update" : ""),
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

  private void requireActiveForManualSync(DataSourceEntity entity) {
    if (!"active".equals(entity.status())) {
      throw new AppException(
          "DATASOURCE_SYNC_NOT_READY", "Datasource must pass its connection test before syncing");
    }
  }

  private String testConnection(DataSourceEntity entity) {
    if (SOURCE_ZABBIX.equals(entity.type())) {
      return zabbixClient(entity).testConnection();
    }
    if (SOURCE_KUBERNETES.equals(entity.type())) {
      return kubernetesClientFactory.create(readKubernetesConfig(entity.configJson())).version();
    }
    if (PASSIVE_SOURCES.contains(entity.type())) {
      return "push";
    }
    throw new AppException("UNSUPPORTED_DATASOURCE", "Datasource cannot be tested");
  }

  private String testSuccessMessage(DataSourceEntity entity) {
    return PASSIVE_SOURCES.contains(entity.type())
        ? "Passive ingestion datasource is ready"
        : entity.type() + " connection succeeded";
  }

  private Object toConfig(String type, CreateDataSourceRequest request) {
    if (SOURCE_ZABBIX.equals(type)) {
      return toZabbixConfig(request.zabbix());
    }
    if (SOURCE_KUBERNETES.equals(type)) {
      return toKubernetesConfig(request.kubernetes());
    }
    if (!PASSIVE_SOURCES.contains(type)) {
      throw new AppException("UNSUPPORTED_DATASOURCE", "Unsupported datasource type");
    }
    return Map.of("endpoint", passiveEndpoint(request.passive()));
  }

  private Object mergeConfig(DataSourceEntity existing, UpdateDataSourceRequest request) {
    return switch (existing.type()) {
      case SOURCE_ZABBIX -> mergeZabbixConfig(existing.configJson(), request.zabbix());
      case SOURCE_KUBERNETES -> mergeKubernetesConfig(existing.configJson(), request.kubernetes());
      default -> {
        if (!PASSIVE_SOURCES.contains(existing.type())) {
          throw new AppException("UNSUPPORTED_DATASOURCE", "Unsupported datasource type");
        }
        String endpoint =
            request.passive() == null ? null : trimToNull(request.passive().endpoint());
        yield Map.of("endpoint", endpoint == null ? "" : endpoint);
      }
    };
  }

  private ZabbixConfig mergeZabbixConfig(
      String configJson, UpdateDataSourceRequest.ZabbixConfig request) {
    ZabbixConfig existing = readZabbixConfig(configJson);
    if (request == null) {
      return existing;
    }
    ZabbixConfig merged =
        new ZabbixConfig(
            trimToNull(request.endpoint()),
            valueOrExisting(request.username(), existing.username()),
            valueOrExisting(request.password(), existing.password()),
            valueOrExisting(request.apiToken(), existing.apiToken()),
            request.connectTimeoutSeconds() == null
                ? existing.connectTimeoutSeconds()
                : request.connectTimeoutSeconds(),
            request.readTimeoutSeconds() == null
                ? existing.readTimeoutSeconds()
                : request.readTimeoutSeconds());
    validateZabbixConfig(merged);
    return merged;
  }

  private KubernetesConfig mergeKubernetesConfig(
      String configJson, UpdateDataSourceRequest.KubernetesConfig request) {
    KubernetesConfig existing = readKubernetesConfig(configJson);
    if (request == null) {
      return existing;
    }
    KubernetesConfig merged =
        new KubernetesConfig(
            trimToNull(request.endpoint()),
            valueOrExisting(request.apiToken(), existing.apiToken()),
            request.timeoutSeconds() == null
                ? existing.timeoutSeconds()
                : request.timeoutSeconds());
    validateKubernetesConfig(merged);
    return merged;
  }

  private String valueOrExisting(String value, String existing) {
    String normalized = trimToNull(value);
    return normalized == null ? existing : normalized;
  }

  private String passiveEndpoint(PassiveDataSourceConfigRequest request) {
    String endpoint = request == null ? null : trimToNull(request.endpoint());
    return endpoint == null ? "" : endpoint;
  }

  private ZabbixClient zabbixClient(DataSourceEntity entity) {
    return zabbixClientFactory.create(readZabbixConfig(entity.configJson()));
  }

  private ZabbixConfig toZabbixConfig(ZabbixConfigRequest request) {
    if (request == null) {
      throw new AppException("DATASOURCE_CONFIG_INVALID", "Zabbix config is required");
    }
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

  private KubernetesConfig toKubernetesConfig(KubernetesConfigRequest request) {
    if (request == null) {
      throw new AppException("DATASOURCE_CONFIG_INVALID", "Kubernetes config is required");
    }
    KubernetesConfig config =
        new KubernetesConfig(
            trimToNull(request.endpoint()),
            trimToNull(request.apiToken()),
            request.timeoutSeconds());
    validateKubernetesConfig(config);
    return config;
  }

  private void validateKubernetesConfig(KubernetesConfig config) {
    if (config.endpoint() == null || config.apiToken() == null) {
      throw new AppException(
          "DATASOURCE_CONFIG_INVALID", "Kubernetes endpoint and apiToken are required");
    }
  }

  private KubernetesConfig readKubernetesConfig(String configJson) {
    try {
      KubernetesConfig config = objectMapper.readValue(configJson, KubernetesConfig.class);
      validateKubernetesConfig(config);
      return config;
    } catch (AppException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new AppException("DATASOURCE_CONFIG_INVALID", "Datasource config is invalid");
    }
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
