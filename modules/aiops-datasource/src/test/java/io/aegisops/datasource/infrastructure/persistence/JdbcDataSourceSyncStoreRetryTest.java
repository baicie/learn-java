package io.aegisops.datasource.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.datasource.application.port.DataSourceSyncStore.SyncRunClaimResult;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcDataSourceSyncStoreRetryTest {
  @Test
  void atomicallyClaimsPendingFailedOrExpiredRunningRunWithAnIndependentLease() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), eq(OffsetDateTime.class), any(Object[].class)))
        .thenReturn(OffsetDateTime.parse("2026-07-27T10:20:00Z"));
    when(jdbc.queryForObject(anyString(), eq(String.class), any(Object[].class)))
        .thenReturn("acquired");
    JdbcDataSourceSyncStore store = new JdbcDataSourceSyncStore(jdbc, new ObjectMapper());

    assertThat(store.claimForExecution("tenant-a", "ds-a", "sync-a", "claim-a"))
        .isEqualTo(SyncRunClaimResult.ACQUIRED);

    ArgumentCaptor<String> datasourceSql = ArgumentCaptor.forClass(String.class);
    verify(jdbc)
        .queryForObject(datasourceSql.capture(), eq(OffsetDateTime.class), any(Object[].class));
    assertThat(normalize(datasourceSql.getValue()))
        .contains("select d.updated_at")
        .contains("join tenant t")
        .contains("t.status = 'active'")
        .contains("d.tenant_id = ?")
        .contains("d.id = ?")
        .contains("type = 'zabbix'")
        .contains("status in ('active', 'error')")
        .contains("for update of d");
    ArgumentCaptor<String> runSql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).queryForObject(runSql.capture(), eq(String.class), any(Object[].class));
    assertThat(normalize(runSql.getValue()))
        .doesNotContain("from automation_outbox")
        .contains("set status = 'running'")
        .contains("claim_token = ?")
        .contains("lease_until = now() + interval '5 minutes'")
        .contains("datasource_updated_at = ?")
        .contains("r.status in ('pending', 'failed')")
        .contains("r.status = 'running' and r.lease_until <= now()")
        .contains("competing.id <> r.id")
        .contains("competing.status = 'running'")
        .contains("competing.status = 'pending'")
        .contains("r.status <> 'pending'")
        .contains("status = 'success'");
  }

  @Test
  void renewsOnlyTheCurrentClaimLease() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    JdbcDataSourceSyncStore store = new JdbcDataSourceSyncStore(jdbc, new ObjectMapper());
    OffsetDateTime leaseUntil = OffsetDateTime.parse("2026-07-27T10:20:30Z");

    assertThat(store.renewClaim("tenant-a", "ds-a", "sync-a", "claim-a", leaseUntil)).isTrue();

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).update(sql.capture(), any(Object[].class));
    assertThat(normalize(sql.getValue()))
        .contains("set lease_until = ?")
        .contains("r.tenant_id = ?")
        .contains("r.datasource_id = ?")
        .contains("r.id = ?")
        .contains("r.status = 'running'")
        .contains("r.claim_token = ?")
        .contains("join tenant t")
        .contains("d.updated_at = r.datasource_updated_at");
  }

  @Test
  void mapsSuccessfulRunToIdempotentCompletion() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), eq(OffsetDateTime.class), any(Object[].class)))
        .thenReturn(OffsetDateTime.parse("2026-07-27T10:20:00Z"));
    when(jdbc.queryForObject(anyString(), eq(String.class), any(Object[].class)))
        .thenReturn("already_completed");
    JdbcDataSourceSyncStore store = new JdbcDataSourceSyncStore(jdbc, new ObjectMapper());

    assertThat(store.claimForExecution("tenant-a", "ds-a", "sync-a", "claim-a"))
        .isEqualTo(SyncRunClaimResult.ALREADY_COMPLETED);
  }

  @Test
  void terminalUpdatesAreFencedByClaimAndClearTheLease() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    when(jdbc.queryForObject(anyString(), eq(String.class), any(Object[].class)))
        .thenReturn("ds-a");
    JdbcDataSourceSyncStore store = new JdbcDataSourceSyncStore(jdbc, new ObjectMapper());

    assertThat(
            store.completeClaimed(
                "tenant-a", "ds-a", "sync-a", "claim-a", Map.of("hostsCreated", 1)))
        .isTrue();
    assertThat(
            store.failClaimed(
                "tenant-a", "ds-a", "sync-b", "claim-b", Map.of("hostsCreated", 0), "timeout"))
        .isTrue();

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc, times(4)).update(sql.capture(), any(Object[].class));
    String completeSql = normalize(sql.getAllValues().get(0));
    String completeDatasourceSql = normalize(sql.getAllValues().get(1));
    String failSql = normalize(sql.getAllValues().get(2));
    String failDatasourceSql = normalize(sql.getAllValues().get(3));
    assertThat(completeSql)
        .contains("status='success'")
        .contains("lease_until=null")
        .contains("claim_token=null")
        .contains("tenant_id=? and datasource_id=? and id=?")
        .contains("status='running' and claim_token=?");
    assertThat(completeDatasourceSql).contains("status in ('active', 'error')");
    assertThat(failSql)
        .contains("status='failed'")
        .contains("lease_until=null")
        .contains("claim_token=null")
        .contains("tenant_id=? and datasource_id=? and id=?")
        .contains("status='running' and claim_token=?");
    assertThat(failDatasourceSql).contains("status in ('active', 'error')");
  }

  @Test
  void staleClaimCannotChangeTheRunOrDatasourceStatus() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), eq(String.class), any(Object[].class)))
        .thenThrow(new EmptyResultDataAccessException(1));
    JdbcDataSourceSyncStore store = new JdbcDataSourceSyncStore(jdbc, new ObjectMapper());

    assertThat(store.completeClaimed("tenant-a", "ds-a", "sync-a", "stale-claim", Map.of()))
        .isFalse();

    verify(jdbc, never()).update(anyString(), any(Object[].class));
    verify(jdbc, never())
        .update(
            org.mockito.ArgumentMatchers.contains("update datasource set"), any(Object[].class));
  }

  @Test
  void retryCanLoadZabbixConfigurationWhileDatasourceStatusIsError() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), eq(String.class), any(Object[].class)))
        .thenReturn(
            "{\"endpoint\":\"https://zabbix.example\",\"apiToken\":\"secret\","
                + "\"connectTimeoutSeconds\":3,\"readTimeoutSeconds\":5}");
    JdbcDataSourceSyncStore store = new JdbcDataSourceSyncStore(jdbc, new ObjectMapper());

    assertThat(store.loadZabbixConfig("tenant-a", "ds-a").endpoint())
        .isEqualTo("https://zabbix.example");

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).queryForObject(sql.capture(), eq(String.class), any(Object[].class));
    assertThat(normalize(sql.getValue()))
        .contains("tenant_id=? and id=? and type='zabbix'")
        .doesNotContain("status='active'");
  }

  private String normalize(String sql) {
    return sql.replaceAll("\\s+", " ").toLowerCase();
  }
}
