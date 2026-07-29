package io.aegisops.integration.infrastructure.persistence;

import io.aegisops.integration.application.port.ZabbixWebhookDatasourceStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcZabbixWebhookDatasourceStore implements ZabbixWebhookDatasourceStore {
  private final JdbcTemplate jdbc;

  public JdbcZabbixWebhookDatasourceStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean existsZabbix(String tenantId, String datasourceId) {
    Boolean exists =
        jdbc.queryForObject(
            """
            select exists (
              select 1
              from datasource d
              join tenant t on t.id = d.tenant_id and t.status = 'active'
              where d.tenant_id = ?
                and d.id = ?
                and d.type = 'zabbix'
                and d.status = 'active'
            )
            """,
            Boolean.class,
            tenantId,
            datasourceId);
    return Boolean.TRUE.equals(exists);
  }
}
