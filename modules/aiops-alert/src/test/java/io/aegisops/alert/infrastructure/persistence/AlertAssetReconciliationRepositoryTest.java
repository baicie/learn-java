package io.aegisops.alert.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class AlertAssetReconciliationRepositoryTest {
  @Test
  void scopesBackfillByTenantDatasourceAndExternalHostWithoutOverwritingAssets() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(2);
    AlertAssetReconciliationRepository repository = new AlertAssetReconciliationRepository(jdbc);

    assertThat(
            repository.backfillZabbixAssetId("tenant-a", "datasource-a", "host-10084", "asset-a"))
        .isEqualTo(2);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(sql.capture(), arguments.capture());
    assertThat(normalize(sql.getValue()))
        .contains("tenant_id = ?")
        .contains("source = 'zabbix'")
        .contains("asset_id is null")
        .contains("labels ->> 'datasourceid' = ?")
        .contains("labels ->> 'zabbixhostid' = ?")
        .contains("'zabbixhostids'");
    assertThat(arguments.getValue())
        .containsExactly("asset-a", "tenant-a", "datasource-a", "host-10084", "host-10084");
  }

  private static String normalize(String sql) {
    return sql.replaceAll("\\s+", " ").toLowerCase();
  }
}
