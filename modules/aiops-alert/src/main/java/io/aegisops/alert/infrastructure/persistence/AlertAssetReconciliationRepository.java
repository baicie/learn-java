package io.aegisops.alert.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AlertAssetReconciliationRepository {
  private final JdbcTemplate jdbc;

  public AlertAssetReconciliationRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public int backfillZabbixAssetId(
      String tenantId, String datasourceId, String externalHostId, String assetId) {
    return jdbc.update(
        """
            update alert_event
            set asset_id = ?,
                updated_at = now()
            where tenant_id = ?
              and source = 'zabbix'
              and asset_id is null
              and labels ->> 'datasourceId' = ?
              and (
                labels ->> 'zabbixHostId' = ?
                or labels @> jsonb_build_object(
                  'zabbixHostIds', jsonb_build_array(?::text)
                )
              )
            """,
        assetId,
        tenantId,
        datasourceId,
        externalHostId,
        externalHostId);
  }
}
