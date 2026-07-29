package io.aegisops.datasource.infrastructure.persistence;

import io.aegisops.datasource.application.port.ZabbixSyncDispatchStore;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcZabbixSyncDispatchStore implements ZabbixSyncDispatchStore {
  private final JdbcTemplate jdbc;

  public JdbcZabbixSyncDispatchStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<SyncTarget> lockDueTargets(OffsetDateTime dueBefore, int batchSize) {
    return jdbc.query(
        """
        select d.tenant_id, d.id as datasource_id
        from datasource d
        join tenant t on t.id = d.tenant_id and t.status = 'active'
        where d.type = 'zabbix'
          and d.status = 'active'
          and (d.last_sync_at is null or d.last_sync_at <= ?)
          and not exists (
            select 1
            from datasource_sync_run r
            where r.tenant_id = d.tenant_id
              and r.datasource_id = d.id
              and (
                (
                  r.status = 'pending'
                  and not exists (
                    select 1
                    from automation_outbox o
                    where o.tenant_id = r.tenant_id
                      and o.target_app = 'worker'
                      and o.job_name = 'zabbix-sync'
                      and o.status = 'failed'
                      and o.payload->>'tenantId' = r.tenant_id
                      and o.payload->>'datasourceId' = r.datasource_id
                      and o.payload->>'runId' = r.id
                  )
                )
                or (
                  r.status = 'running'
                  and (r.lease_until is null or r.lease_until > now())
                )
              )
        )
        order by d.last_sync_at asc nulls first, d.created_at, d.id
        limit ?
        for update of d skip locked
        """,
        (rs, rowNum) -> new SyncTarget(rs.getString("tenant_id"), rs.getString("datasource_id")),
        dueBefore,
        batchSize);
  }

  @Override
  public int failPendingRunsWithTerminalDispatchFailure(String tenantId, String datasourceId) {
    return jdbc.update(
        """
        update datasource_sync_run r
        set status = 'failed',
            message = 'Sync dispatch failed before execution',
            finished_at = now(),
            lease_until = null,
            claim_token = null
        where r.tenant_id = ?
          and r.datasource_id = ?
          and r.status = 'pending'
          and exists (
            select 1
            from automation_outbox o
            where o.tenant_id = r.tenant_id
              and o.target_app = 'worker'
              and o.job_name = 'zabbix-sync'
              and o.status = 'failed'
              and o.payload->>'tenantId' = r.tenant_id
              and o.payload->>'datasourceId' = r.datasource_id
              and o.payload->>'runId' = r.id
          )
        """,
        tenantId,
        datasourceId);
  }

  @Override
  public boolean createScheduledRun(
      String runId, String tenantId, String datasourceId, OffsetDateTime startedAt) {
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
              id, tenant_id, datasource_id, sync_type, status, started_at, created_by
            )
            select ?, ?, ?, 'scheduled', 'pending', ?, 'system:zabbix-scheduler'
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
            on conflict (id) do nothing
            """,
            tenantId,
            datasourceId,
            runId,
            tenantId,
            datasourceId,
            startedAt,
            tenantId,
            datasourceId)
        == 1;
  }
}
