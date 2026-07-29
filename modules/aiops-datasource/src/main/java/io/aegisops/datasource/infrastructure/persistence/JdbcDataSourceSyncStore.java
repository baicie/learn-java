package io.aegisops.datasource.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.datasource.application.port.DataSourceSyncStore;
import io.aegisops.datasource.application.port.DataSourceSyncStore.SyncRunClaimResult;
import io.aegisops.kubernetes.domain.model.KubernetesConfig;
import io.aegisops.zabbix.ZabbixConfig;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcDataSourceSyncStore implements DataSourceSyncStore {
  private static final String CLAIM_FOR_EXECUTION_SQL =
      """
      with claimed as (
        update datasource_sync_run r
        set status = 'running',
            message = null,
            finished_at = null,
            claim_token = ?,
            lease_until = now() + interval '5 minutes',
            datasource_updated_at = ?
        where r.tenant_id = ?
          and r.datasource_id = ?
          and r.id = ?
          and (
            r.status in ('pending', 'failed')
            or (r.status = 'running' and r.lease_until <= now())
          )
          and not exists (
            select 1
            from datasource_sync_run competing
            where competing.tenant_id = r.tenant_id
              and competing.datasource_id = r.datasource_id
              and competing.id <> r.id
              and (
                (
                  competing.status = 'running'
                  and (
                    competing.lease_until is null
                    or competing.lease_until > now()
                  )
                )
                or (
                  competing.status = 'pending'
                  and (
                    r.status <> 'pending'
                    or competing.started_at < r.started_at
                    or (
                      competing.started_at = r.started_at
                      and competing.id < r.id
                    )
                  )
                )
              )
          )
        returning r.id
      ), existing as (
        select r.status
        from datasource_sync_run r
        where r.tenant_id = ?
          and r.datasource_id = ?
          and r.id = ?
      )
      select case
        when exists (select 1 from claimed) then 'acquired'
        when exists (select 1 from existing where status = 'success')
          then 'already_completed'
        when exists (select 1 from existing) then 'active'
        else 'not_found'
      end
      """;

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public JdbcDataSourceSyncStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean createZabbixManualRun(String tenantId, String datasourceId, String runId) {
    return jdbc.update(
            """
            with expired as (
              update datasource_sync_run
              set status = 'failed',
                  message = 'Sync lease expired',
                  finished_at = now(),
                  lease_until = null,
                  claim_token = null
              where tenant_id = ?
                and datasource_id = ?
                and status = 'running'
                and lease_until <= now()
            )
            insert into datasource_sync_run(
              id, tenant_id, datasource_id, sync_type, status, started_at
            )
            select ?, ?, ?, 'manual', 'pending', now()
            where not exists (
              select 1
              from datasource_sync_run
              where tenant_id = ?
                and datasource_id = ?
                and (
                  status = 'pending'
                  or (
                    status = 'running'
                    and (lease_until is null or lease_until > now())
                  )
                )
            )
            """,
            tenantId,
            datasourceId,
            runId,
            tenantId,
            datasourceId,
            tenantId,
            datasourceId)
        == 1;
  }

  @Override
  public boolean createLegacyManualRun(String tenantId, String datasourceId, String runId) {
    return jdbc.update(
            """
            insert into datasource_sync_run(
              id, tenant_id, datasource_id, sync_type, status, started_at
            ) values (?, ?, ?, 'manual', 'pending', now())
            """,
            runId,
            tenantId,
            datasourceId)
        == 1;
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
  @Transactional
  public SyncRunClaimResult claimForExecution(
      String tenantId, String datasourceId, String runId, String claimToken) {
    OffsetDateTime datasourceUpdatedAt;
    try {
      datasourceUpdatedAt = lockActiveDatasource(tenantId, datasourceId);
    } catch (EmptyResultDataAccessException exception) {
      abandonPendingRun(tenantId, datasourceId, runId);
      return SyncRunClaimResult.NOT_FOUND;
    }
    String result = claimRun(tenantId, datasourceId, runId, claimToken, datasourceUpdatedAt);
    return mapClaimResult(result);
  }

  private OffsetDateTime lockActiveDatasource(String tenantId, String datasourceId) {
    return jdbc.queryForObject(
        """
        select d.updated_at
        from datasource d
        join tenant t on t.id = d.tenant_id and t.status = 'active'
        where d.tenant_id = ?
          and d.id = ?
          and d.type = 'zabbix'
          and d.status in ('active', 'error')
        for update of d
        """,
        OffsetDateTime.class,
        tenantId,
        datasourceId);
  }

  private String claimRun(
      String tenantId,
      String datasourceId,
      String runId,
      String claimToken,
      OffsetDateTime datasourceUpdatedAt) {
    return jdbc.queryForObject(
        CLAIM_FOR_EXECUTION_SQL,
        String.class,
        claimToken,
        datasourceUpdatedAt,
        tenantId,
        datasourceId,
        runId,
        tenantId,
        datasourceId,
        runId);
  }

  private SyncRunClaimResult mapClaimResult(String result) {
    if (result == null) {
      return SyncRunClaimResult.NOT_FOUND;
    }
    return switch (result) {
      case "acquired" -> SyncRunClaimResult.ACQUIRED;
      case "already_completed" -> SyncRunClaimResult.ALREADY_COMPLETED;
      case "active" -> SyncRunClaimResult.ACTIVE;
      default -> throw new IllegalStateException("Unknown sync run claim result: " + result);
    };
  }

  @Override
  public boolean renewClaim(
      String tenantId,
      String datasourceId,
      String runId,
      String claimToken,
      OffsetDateTime leaseUntil) {
    return jdbc.update(
            """
            with locked_datasource as materialized (
              select d.tenant_id, d.id, d.updated_at
              from datasource d
              join tenant t on t.id = d.tenant_id and t.status = 'active'
              where d.tenant_id = ?
                and d.id = ?
                and d.type = 'zabbix'
                and d.status in ('active', 'error')
              for update of d
            )
            update datasource_sync_run r
            set lease_until = ?
            from locked_datasource d
            where r.tenant_id = ?
              and r.datasource_id = ?
              and r.id = ?
              and r.status = 'running'
              and r.claim_token = ?
              and r.lease_until > now()
              and d.tenant_id = r.tenant_id
              and d.id = r.datasource_id
              and d.updated_at = r.datasource_updated_at
            """,
            tenantId,
            datasourceId,
            leaseUntil,
            tenantId,
            datasourceId,
            runId,
            claimToken)
        == 1;
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
  @Transactional
  public void complete(
      String tenantId, String datasourceId, String runId, Map<String, Object> statistics) {
    int updated =
        jdbc.update(
            "update datasource_sync_run set status='success',message='Sync completed',stats_json=?::jsonb,finished_at=now(),lease_until=null,claim_token=null where tenant_id=? and datasource_id=? and id=? and status='running' and claim_token is null",
            json(statistics),
            tenantId,
            datasourceId,
            runId);
    if (updated == 1) {
      markDatasourceActive(tenantId, datasourceId);
    }
  }

  @Override
  @Transactional
  public void fail(
      String tenantId,
      String datasourceId,
      String runId,
      Map<String, Object> statistics,
      String message) {
    int updated =
        jdbc.update(
            "update datasource_sync_run set status='failed',message=?,stats_json=?::jsonb,finished_at=now(),lease_until=null,claim_token=null where tenant_id=? and datasource_id=? and id=? and status='running' and claim_token is null",
            message,
            json(statistics),
            tenantId,
            datasourceId,
            runId);
    if (updated == 1) {
      markDatasourceError(tenantId, datasourceId);
    }
  }

  @Override
  @Transactional
  public boolean completeClaimed(
      String tenantId,
      String datasourceId,
      String runId,
      String claimToken,
      Map<String, Object> statistics) {
    if (!lockClaimedDatasource(tenantId, datasourceId, runId, claimToken)) {
      return false;
    }
    int updated =
        jdbc.update(
            "update datasource_sync_run set status='success',message='Sync completed',stats_json=?::jsonb,finished_at=now(),lease_until=null,claim_token=null where tenant_id=? and datasource_id=? and id=? and status='running' and claim_token=?",
            json(statistics),
            tenantId,
            datasourceId,
            runId,
            claimToken);
    if (updated == 1) {
      markDatasourceActive(tenantId, datasourceId);
    }
    return updated == 1;
  }

  @Override
  @Transactional
  public boolean failClaimed(
      String tenantId,
      String datasourceId,
      String runId,
      String claimToken,
      Map<String, Object> statistics,
      String message) {
    if (!lockClaimedDatasource(tenantId, datasourceId, runId, claimToken)) {
      return false;
    }
    int updated =
        jdbc.update(
            "update datasource_sync_run set status='failed',message=?,stats_json=?::jsonb,finished_at=now(),lease_until=null,claim_token=null where tenant_id=? and datasource_id=? and id=? and status='running' and claim_token=?",
            message,
            json(statistics),
            tenantId,
            datasourceId,
            runId,
            claimToken);
    if (updated == 1) {
      markDatasourceError(tenantId, datasourceId);
    }
    return updated == 1;
  }

  private void markDatasourceActive(String tenantId, String datasourceId) {
    jdbc.update(
        "update datasource set status='active',last_sync_at=now(),updated_at=now() where tenant_id=? and id=? and status in ('active', 'error')",
        tenantId,
        datasourceId);
  }

  private void markDatasourceError(String tenantId, String datasourceId) {
    jdbc.update(
        "update datasource set status='error',updated_at=now() where tenant_id=? and id=? and status in ('active', 'error')",
        tenantId,
        datasourceId);
  }

  private void abandonPendingRun(String tenantId, String datasourceId, String runId) {
    jdbc.update(
        """
        update datasource_sync_run r
        set status = 'failed',
            message = 'Sync target is no longer active',
            finished_at = now(),
            lease_until = null,
            claim_token = null
        where r.tenant_id = ?
          and r.datasource_id = ?
          and r.id = ?
          and r.status = 'pending'
          and not exists (
            select 1
            from datasource d
            join tenant t on t.id = d.tenant_id and t.status = 'active'
            where d.tenant_id = r.tenant_id
              and d.id = r.datasource_id
              and d.type = 'zabbix'
              and d.status in ('active', 'error')
          )
        """,
        tenantId,
        datasourceId,
        runId);
  }

  private boolean lockClaimedDatasource(
      String tenantId, String datasourceId, String runId, String claimToken) {
    try {
      String lockedDatasourceId =
          jdbc.queryForObject(
              """
              select d.id
              from datasource_sync_run r
              join datasource d
                on d.tenant_id = r.tenant_id
               and d.id = r.datasource_id
              join tenant t on t.id = d.tenant_id and t.status = 'active'
              where r.tenant_id = ?
                and r.datasource_id = ?
                and r.id = ?
                and r.status = 'running'
                and r.claim_token = ?
                and r.lease_until > now()
                and d.type = 'zabbix'
                and d.status in ('active', 'error')
                and d.updated_at = r.datasource_updated_at
              for update of d
              """,
              String.class,
              tenantId,
              datasourceId,
              runId,
              claimToken);
      return lockedDatasourceId != null;
    } catch (EmptyResultDataAccessException exception) {
      return false;
    }
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("sync stats cannot be serialized", exception);
    }
  }
}
