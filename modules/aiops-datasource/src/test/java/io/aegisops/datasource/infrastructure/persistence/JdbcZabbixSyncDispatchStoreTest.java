package io.aegisops.datasource.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcZabbixSyncDispatchStoreTest {
  @Test
  void scheduledRunInsertRechecksThatNoInflightRunExists() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    JdbcZabbixSyncDispatchStore store = new JdbcZabbixSyncDispatchStore(jdbc);

    assertThat(
            store.createScheduledRun(
                "sync-a", "tenant-a", "ds-a", OffsetDateTime.parse("2026-07-27T10:15:30Z")))
        .isTrue();

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).update(sql.capture(), any(Object[].class));
    assertThat(sql.getValue().replaceAll("\\s+", " ").toLowerCase())
        .contains("where not exists")
        .contains("tenant_id = ?")
        .contains("datasource_id = ?")
        .contains("status = 'failed'")
        .contains("lease_until <= now()")
        .contains("status = 'pending'")
        .contains("status = 'running'")
        .contains("lease_until is null")
        .contains("lease_until > now()");
  }
}
