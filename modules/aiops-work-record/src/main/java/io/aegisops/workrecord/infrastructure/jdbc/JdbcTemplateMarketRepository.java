package io.aegisops.workrecord.infrastructure.jdbc;

import io.aegisops.common.id.Ids;
import io.aegisops.workrecord.application.port.TemplateMarketRepository;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTemplateMarketRepository implements TemplateMarketRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcTemplateMarketRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public MarketVersion publish(PublishPackage command) {
    String tenantId = command.tenantId();
    String code = command.packageCode();
    String packageId =
        jdbc
            .queryForList(
                "select id from work_record.wr_market_package where package_code=:code",
                Map.of("code", code),
                String.class)
            .stream()
            .findFirst()
            .orElse(null);
    if (packageId == null) {
      packageId = Ids.newId();
      jdbc.update(
          """
          insert into work_record.wr_market_package(
           id,publisher_tenant_id,source_template_id,package_code,name,category,visibility,status,created_by)
          values(:id,:tenantId,:sourceTemplateId,:code,:name,:category,:visibility,'published',:actor)
          """,
          Map.of(
              "id",
              packageId,
              "tenantId",
              tenantId,
              "sourceTemplateId",
              command.sourceTemplateId(),
              "code",
              code,
              "name",
              command.name(),
              "category",
              command.category(),
              "visibility",
              command.visibility(),
              "actor",
              command.actorId()));
    } else {
      Long owned =
          jdbc.queryForObject(
              "select count(*) from work_record.wr_market_package where id=:id and publisher_tenant_id=:tenantId",
              Map.of("id", packageId, "tenantId", tenantId),
              Long.class);
      if (owned == null || owned == 0)
        throw new IllegalStateException("package code belongs to another tenant");
    }
    Integer version =
        jdbc.queryForObject(
            "select coalesce(max(version_no),0)+1 from work_record.wr_market_package_version where package_id=:id",
            Map.of("id", packageId),
            Integer.class);
    String versionId = Ids.newId();
    jdbc.update(
        """
        insert into work_record.wr_market_package_version(
         id,package_id,version_no,version_name,package_json,checksum,published_by)
        values(:id,:packageId,:version,:versionName,cast(:json as jsonb),:checksum,:actor)
        """,
        Map.of(
            "id",
            versionId,
            "packageId",
            packageId,
            "version",
            version,
            "versionName",
            "v" + version,
            "json",
            command.packageJson(),
            "checksum",
            command.checksum(),
            "actor",
            command.actorId()));
    jdbc.update(
        "update work_record.wr_market_package set latest_version_id=:versionId,status='published',"
            + "updated_at=now() where id=:packageId",
        Map.of("versionId", versionId, "packageId", packageId));
    return requireVisible(tenantId, versionId);
  }

  @Override
  public MarketVersion requireVisible(String tenantId, String versionId) {
    return jdbc
        .query(
            select()
                + " where v.id=:id and p.status='published' and "
                + "(p.visibility='public' or p.publisher_tenant_id=:tenantId)",
            Map.of("id", versionId, "tenantId", tenantId),
            this::map)
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("market package version not found"));
  }

  @Override
  public List<MarketVersion> listVisible(String tenantId) {
    return jdbc.query(
        select()
            + " where p.status='published' and v.id=p.latest_version_id and "
            + "(p.visibility='public' or p.publisher_tenant_id=:tenantId) order by p.updated_at desc",
        Map.of("tenantId", tenantId),
        this::map);
  }

  @Override
  public void recordInstall(
      String id,
      String tenantId,
      String packageId,
      String versionId,
      String templateId,
      String actorId) {
    jdbc.update(
        """
        insert into work_record.wr_market_install(
         id,tenant_id,package_id,package_version_id,installed_template_id,installed_by)
        values(:id,:tenantId,:packageId,:versionId,:templateId,:actor)
        """,
        Map.of(
            "id",
            id,
            "tenantId",
            tenantId,
            "packageId",
            packageId,
            "versionId",
            versionId,
            "templateId",
            templateId,
            "actor",
            actorId));
    jdbc.update(
        "update work_record.wr_market_package set install_count=install_count+1 where id=:id",
        Map.of("id", packageId));
  }

  private MarketVersion map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    return new MarketVersion(
        rs.getString("version_id"),
        rs.getString("package_id"),
        rs.getString("package_code"),
        rs.getString("name"),
        rs.getString("visibility"),
        rs.getInt("version_no"),
        rs.getString("package_json"),
        rs.getString("checksum"));
  }

  private static String select() {
    return "select v.id version_id,p.id package_id,p.package_code,p.name,p.visibility,v.version_no,"
        + "v.package_json::text,v.checksum from work_record.wr_market_package p "
        + "join work_record.wr_market_package_version v on v.package_id=p.id";
  }
}
