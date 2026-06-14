package io.aegisops.datasource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.zabbix.ZabbixClient;
import io.aegisops.zabbix.ZabbixClientFactory;
import io.aegisops.zabbix.ZabbixConfig;
import io.aegisops.zabbix.ZabbixHost;
import io.aegisops.zabbix.ZabbixProblem;
import io.aegisops.zabbix.ZabbixSeverity;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DataSourceService {
    private static final String SOURCE_ZABBIX = "zabbix";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ZabbixClientFactory zabbixClientFactory;

    public DataSourceService(JdbcTemplate jdbc, ObjectMapper objectMapper, ZabbixClientFactory zabbixClientFactory) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.zabbixClientFactory = zabbixClientFactory;
    }

    public List<DataSourceRecord> list(String tenantId) {
        return jdbc.query("""
                select id, tenant_id, type, name, status, created_at, updated_at, last_sync_at
                from datasource where tenant_id = ? order by created_at desc
                """, (rs, rowNum) -> new DataSourceRecord(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("type"),
                rs.getString("name"),
                rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("last_sync_at", OffsetDateTime.class)
        ), tenantId);
    }

    public DataSourceRecord create(String tenantId, CreateDataSourceRequest request) {
        String type = normalizeType(request.type());
        if (!SOURCE_ZABBIX.equals(type)) {
            throw new AppException("UNSUPPORTED_DATASOURCE", "Only zabbix datasource is supported in Phase1");
        }

        ZabbixConfig config = toZabbixConfig(request.zabbix());
        String id = newId("ds");
        String configJson = writeJson(config);

        jdbc.update("""
                insert into datasource(id, tenant_id, type, name, status, config_json, created_at, updated_at)
                values (?, ?, ?, ?, 'inactive', ?::jsonb, now(), now())
                """, id, tenantId, type, request.name().trim(), configJson);
        return getRecord(tenantId, id);
    }

    public TestDataSourceResponse test(String tenantId, String id) {
        DataSourceEntity entity = getEntity(tenantId, id);
        ensureZabbix(entity);
        try {
            String version = zabbixClient(entity).testConnection();
            jdbc.update("update datasource set status = 'active', updated_at = now() where tenant_id = ? and id = ?",
                    tenantId, id);
            return new TestDataSourceResponse(true, "Zabbix connection succeeded", version);
        } catch (RuntimeException ex) {
            jdbc.update("update datasource set status = 'error', updated_at = now() where tenant_id = ? and id = ?",
                    tenantId, id);
            return new TestDataSourceResponse(false, ex.getMessage(), null);
        }
    }

    public SyncDataSourceResponse sync(String tenantId, String id) {
        DataSourceEntity entity = getEntity(tenantId, id);
        ensureZabbix(entity);

        String runId = newId("sync");
        jdbc.update("""
                insert into datasource_sync_run(id, tenant_id, datasource_id, sync_type, status, started_at)
                values (?, ?, ?, 'manual', 'running', now())
                """, runId, tenantId, id);

        SyncStats stats = new SyncStats();
        try {
            ZabbixClient client = zabbixClient(entity);
            for (ZabbixHost host : client.getHosts(1000)) {
                UpsertResult result = upsertHostAsset(tenantId, id, host);
                if (result.created()) {
                    stats.hostsCreated++;
                } else {
                    stats.hostsUpdated++;
                }
            }

            for (ZabbixProblem problem : client.getProblems(1000)) {
                UpsertResult result = upsertAlertEvent(tenantId, id, problem);
                if (result.created()) {
                    stats.alertsCreated++;
                } else {
                    stats.alertsUpdated++;
                }
            }
            String statsJson = writeJson(stats.toMap());
            jdbc.update("""
                    update datasource_sync_run
                    set status = 'success', message = ?, stats_json = ?::jsonb, finished_at = now()
                    where tenant_id = ? and id = ?
                    """, "Sync completed", statsJson, tenantId, runId);
            jdbc.update("update datasource set status = 'active', last_sync_at = now(), updated_at = now() where tenant_id = ? and id = ?",
                    tenantId, id);

            return new SyncDataSourceResponse(runId, "success", stats.hostsCreated, stats.hostsUpdated,
                    stats.alertsCreated, stats.alertsUpdated, "Sync completed");
        } catch (RuntimeException ex) {
            String statsJson = writeJson(stats.toMap());
            jdbc.update("""
                    update datasource_sync_run
                    set status = 'failed', message = ?, stats_json = ?::jsonb, finished_at = now()
                    where tenant_id = ? and id = ?
                    """, ex.getMessage(), statsJson, tenantId, runId);
            jdbc.update("update datasource set status = 'error', updated_at = now() where tenant_id = ? and id = ?",
                    tenantId, id);
            throw new AppException("DATASOURCE_SYNC_FAILED", ex.getMessage());
        }
    }

    public List<SyncRunRecord> syncRuns(String tenantId, String datasourceId) {
        ensureExists(tenantId, datasourceId);
        return jdbc.query("""
                select id, datasource_id, sync_type, status, message, stats_json::text, started_at, finished_at
                from datasource_sync_run
                where tenant_id = ? and datasource_id = ?
                order by started_at desc
                limit 20
                """, (rs, rowNum) -> new SyncRunRecord(
                rs.getString("id"),
                rs.getString("datasource_id"),
                rs.getString("sync_type"),
                rs.getString("status"),
                rs.getString("message"),
                rs.getString("stats_json"),
                rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("finished_at", OffsetDateTime.class)
        ), tenantId, datasourceId);
    }

    private DataSourceRecord getRecord(String tenantId, String id) {
        return jdbc.queryForObject("""
                select id, tenant_id, type, name, status, created_at, updated_at, last_sync_at
                from datasource where tenant_id = ? and id = ?
                """, (rs, rowNum) -> new DataSourceRecord(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("type"),
                rs.getString("name"),
                rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("last_sync_at", OffsetDateTime.class)
        ), tenantId, id);
    }

    private DataSourceEntity getEntity(String tenantId, String id) {
        try {
            return jdbc.queryForObject("""
                    select id, tenant_id, type, name, status, config_json::text, created_at, updated_at, last_sync_at
                    from datasource where tenant_id = ? and id = ?
                    """, (rs, rowNum) -> new DataSourceEntity(
                    rs.getString("id"),
                    rs.getString("tenant_id"),
                    rs.getString("type"),
                    rs.getString("name"),
                    rs.getString("status"),
                    rs.getString("config_json"),
                    rs.getObject("created_at", OffsetDateTime.class),
                    rs.getObject("updated_at", OffsetDateTime.class),
                    rs.getObject("last_sync_at", OffsetDateTime.class)
            ), tenantId, id);
        } catch (EmptyResultDataAccessException ex) {
            throw new AppException("DATASOURCE_NOT_FOUND", "Datasource not found");
        }
    }

    private void ensureExists(String tenantId, String datasourceId) {
        getEntity(tenantId, datasourceId);
    }

    private void ensureZabbix(DataSourceEntity entity) {
        if (!SOURCE_ZABBIX.equals(entity.type())) {
            throw new AppException("UNSUPPORTED_DATASOURCE", "Only zabbix datasource can be tested or synced in Phase1");
        }
    }

    private ZabbixClient zabbixClient(DataSourceEntity entity) {
        return zabbixClientFactory.create(readZabbixConfig(entity.configJson()));
    }

    private ZabbixConfig toZabbixConfig(ZabbixConfigRequest request) {
        ZabbixConfig config = new ZabbixConfig(
                trimToNull(request.endpoint()),
                trimToNull(request.username()),
                trimToNull(request.password()),
                trimToNull(request.apiToken()),
                request.connectTimeoutSeconds(),
                request.readTimeoutSeconds()
        );

        validateZabbixConfig(config);
        return config;
    }

    private void validateZabbixConfig(ZabbixConfig config) {
        if (config.endpoint() == null || config.endpoint().isBlank()) {
            throw new AppException("DATASOURCE_CONFIG_INVALID", "Zabbix endpoint is required");
        }

        if (!config.hasAuthentication()) {
            throw new AppException("DATASOURCE_CONFIG_INVALID", "Zabbix username/password or apiToken is required");
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

    private UpsertResult upsertHostAsset(String tenantId, String datasourceId, ZabbixHost host) {
        if (host.hostId() == null || host.hostId().isBlank()) {
            return UpsertResult.asUpdated();
        }

        String sourceId = zabbixSourceId(datasourceId, host.hostId());
        String name = firstNonBlank(host.host(), host.name());
        String displayName = firstNonBlank(host.name(), host.host());
        String tagsJson = writeJson(Map.of(
                "datasourceId", datasourceId,
                "zabbixHostId", host.hostId(),
                "groups", host.groups()
        ));
        String status = "1".equals(host.status()) ? "disabled" : "active";

        Boolean created = jdbc.queryForObject("""
                insert into asset(id, tenant_id, asset_type, name, display_name, source, source_id, ip, tags, status, created_at, updated_at)
                values (?, ?, 'host', ?, ?, 'zabbix', ?, ?, ?::jsonb, ?, now(), now())
                on conflict (tenant_id, source, source_id) where source_id is not null
                do update set
                  name = excluded.name,
                  display_name = excluded.display_name,
                  ip = excluded.ip,
                  tags = excluded.tags,
                  status = excluded.status,
                  updated_at = now()
                returning (xmax = 0) as created
                """, Boolean.class, newId("asset"), tenantId, name, displayName, sourceId, host.ip(), tagsJson, status);

        return Boolean.TRUE.equals(created) ? UpsertResult.asCreated() : UpsertResult.asUpdated();
    }

    private UpsertResult upsertAlertEvent(String tenantId, String datasourceId, ZabbixProblem problem) {
        if (problem.eventId() == null || problem.eventId().isBlank()) {
            return UpsertResult.asUpdated();
        }

        String sourceEventId = zabbixSourceId(datasourceId, problem.eventId());
        String assetId = null;

        if (!problem.hostIds().isEmpty()) {
            assetId = findAssetIdBySourceId(tenantId, zabbixSourceId(datasourceId, problem.hostIds().get(0)));
        }

        Map<String, Object> labels = new LinkedHashMap<>();
        labels.put("datasourceId", datasourceId);
        labels.put("zabbixEventId", problem.eventId());
        labels.put("zabbixObjectId", problem.objectId());
        labels.put("zabbixTags", problem.tags());

        String labelsJson = writeJson(labels);
        String rawPayloadJson = writeJson(problem.raw());
        OffsetDateTime startsAt = OffsetDateTime.ofInstant(problem.clock(), ZoneOffset.UTC);
        String severity = mapSeverity(problem.severity());

        String fingerprintKey = firstNonBlank(problem.objectId(), problem.eventId());
        String fingerprint = SOURCE_ZABBIX + ":" + datasourceId + ":" + fingerprintKey;

        Boolean created = jdbc.queryForObject("""
                insert into alert_event(id, tenant_id, source, source_event_id, severity, title, description,
                                        asset_id, entity_type, entity_name, labels, starts_at, status, raw_payload,
                                        fingerprint, created_at)
                values (?, ?, 'zabbix', ?, ?, ?, ?, ?, 'host', ?, ?::jsonb, ?, 'open', ?::jsonb, ?, now())
                on conflict (tenant_id, source, source_event_id) where source_event_id is not null
                do update set
                  severity = excluded.severity,
                  title = excluded.title,
                  description = excluded.description,
                  asset_id = excluded.asset_id,
                  entity_type = excluded.entity_type,
                  entity_name = excluded.entity_name,
                  labels = excluded.labels,
                  starts_at = excluded.starts_at,
                  status = excluded.status,
                  raw_payload = excluded.raw_payload,
                  fingerprint = excluded.fingerprint
                returning (xmax = 0) as created
                """, Boolean.class,
                newId("alert"),
                tenantId,
                sourceEventId,
                severity,
                problem.name(),
                "Zabbix problem event " + problem.eventId(),
                assetId,
                problem.name(),
                labelsJson,
                startsAt,
                rawPayloadJson,
                fingerprint
        );

        return Boolean.TRUE.equals(created) ? UpsertResult.asCreated() : UpsertResult.asUpdated();
    }

    private String findAssetIdBySourceId(String tenantId, String sourceId) {
        try {
            return jdbc.queryForObject("""
                    select id from asset where tenant_id = ? and source = 'zabbix' and source_id = ?
                    """, String.class, tenantId, sourceId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private String normalizeType(String type) {
        return type == null ? "" : type.trim().toLowerCase();
    }

    private String zabbixSourceId(String datasourceId, String zabbixId) {
        return datasourceId + ":" + zabbixId;
    }

    private String mapSeverity(int severity) {
        return ZabbixSeverity.map(severity);
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

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private record UpsertResult(boolean created) {
        static UpsertResult asCreated() {
            return new UpsertResult(true);
        }

        static UpsertResult asUpdated() {
            return new UpsertResult(false);
        }
    }

    private static final class SyncStats {
        int hostsCreated;
        int hostsUpdated;
        int alertsCreated;
        int alertsUpdated;

        Map<String, Object> toMap() {
            return Map.of(
                    "hostsCreated", hostsCreated,
                    "hostsUpdated", hostsUpdated,
                    "alertsCreated", alertsCreated,
                    "alertsUpdated", alertsUpdated
            );
        }
    }
}
