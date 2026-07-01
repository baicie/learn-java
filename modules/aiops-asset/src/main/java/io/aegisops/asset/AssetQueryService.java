package io.aegisops.asset;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AssetQueryService {
  private final JdbcTemplate jdbc;

  public AssetQueryService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<AssetRecord> listRecent(String tenantId) {
    return jdbc.query(
        """
            select id, tenant_id, asset_type, name, display_name, source, status, created_at
            from asset where tenant_id = ? order by created_at desc limit 100
            """,
        (rs, rowNum) ->
            new AssetRecord(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("asset_type"),
                rs.getString("name"),
                rs.getString("display_name"),
                rs.getString("source"),
                rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class)),
        tenantId);
  }
}
