package io.aegisops.integration.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcZabbixWebhookDatasourceStoreTest {
  @Test
  void scopesLookupByActiveTenantDatasourceAndZabbixType() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(Object[].class))).thenReturn(true);
    JdbcZabbixWebhookDatasourceStore store = new JdbcZabbixWebhookDatasourceStore(jdbc);

    assertThat(store.existsZabbix("tenant-a", "ds-a")).isTrue();

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).queryForObject(sql.capture(), eq(Boolean.class), any(Object[].class));
    assertThat(sql.getValue().replaceAll("\\s+", " ").toLowerCase())
        .contains("join tenant t on t.id = d.tenant_id and t.status = 'active'")
        .contains("tenant_id = ?")
        .contains("id = ?")
        .contains("type = 'zabbix'")
        .contains("d.status = 'active'");
  }
}
