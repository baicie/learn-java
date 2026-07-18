package io.aegisops.datasource.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.datasource.application.port.DataSourceSyncStore;
import io.aegisops.kubernetes.domain.model.KubernetesConfig;
import io.aegisops.zabbix.ZabbixConfig;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDataSourceSyncStore implements DataSourceSyncStore {
  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public JdbcDataSourceSyncStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean isPending(String tenantId, String datasourceId, String runId) {
    Integer count =
        jdbc.queryForObject(
            "select count(*) from datasource_sync_run where tenant_id=? and datasource_id=? and id=? and status='pending'",
            Integer.class,
            tenantId,
            datasourceId,
            runId);
    return count != null && count == 1;
  }

  @Override
  public void start(String tenantId, String datasourceId, String runId) {
    jdbc.update(
        "update datasource_sync_run set status='running' where tenant_id=? and datasource_id=? and id=?",
        tenantId,
        datasourceId,
        runId);
  }

  @Override
  public ZabbixConfig loadZabbixConfig(String tenantId, String datasourceId) {
    try {
      String json =
          jdbc.queryForObject(
              "select config_json::text from datasource where tenant_id=? and id=? and type='zabbix'",
              String.class,
              tenantId,
              datasourceId);
      return objectMapper.readValue(json, ZabbixConfig.class);
    } catch (EmptyResultDataAccessException exception) {
      throw new AppException("DATASOURCE_NOT_FOUND", "Datasource not found");
    } catch (JsonProcessingException exception) {
      throw new AppException("DATASOURCE_CONFIG_INVALID", "Datasource config is invalid");
    }
  }

  @Override
  public KubernetesConfig loadKubernetesConfig(String tenantId, String datasourceId) {
    try {
      String json =
          jdbc.queryForObject(
              "select config_json::text from datasource where tenant_id=? and id=? and type='kubernetes'",
              String.class,
              tenantId,
              datasourceId);
      return objectMapper.readValue(json, KubernetesConfig.class);
    } catch (EmptyResultDataAccessException exception) {
      throw new AppException("DATASOURCE_NOT_FOUND", "Datasource not found");
    } catch (JsonProcessingException exception) {
      throw new AppException("DATASOURCE_CONFIG_INVALID", "Datasource config is invalid");
    }
  }

  @Override
  public void complete(
      String tenantId, String datasourceId, String runId, Map<String, Object> statistics) {
    jdbc.update(
        "update datasource_sync_run set status='success',message='Sync completed',stats_json=?::jsonb,finished_at=now() where tenant_id=? and id=?",
        json(statistics),
        tenantId,
        runId);
    jdbc.update(
        "update datasource set status='active',last_sync_at=now(),updated_at=now() where tenant_id=? and id=?",
        tenantId,
        datasourceId);
  }

  @Override
  public void fail(
      String tenantId,
      String datasourceId,
      String runId,
      Map<String, Object> statistics,
      String message) {
    jdbc.update(
        "update datasource_sync_run set status='failed',message=?,stats_json=?::jsonb,finished_at=now() where tenant_id=? and id=?",
        message,
        json(statistics),
        tenantId,
        runId);
    jdbc.update(
        "update datasource set status='error',updated_at=now() where tenant_id=? and id=?",
        tenantId,
        datasourceId);
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("sync stats cannot be serialized", exception);
    }
  }
}
