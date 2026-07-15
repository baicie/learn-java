package io.aegisops.workrecord.infrastructure.jdbc;

import io.aegisops.workrecord.application.port.AiGenerationRepository;
import io.aegisops.workrecord.domain.model.AiGeneration;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAiGenerationRepository implements AiGenerationRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcAiGenerationRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public AiGeneration create(CreateGeneration value) {
    Map<String, Object> p = new HashMap<>();
    p.put("id", value.id());
    p.put("tenantId", value.tenantId());
    p.put("type", value.generationType());
    p.put("resourceType", value.resourceType());
    p.put("resourceId", value.resourceId());
    p.put("periodStart", value.periodStart());
    p.put("periodEnd", value.periodEnd());
    p.put("promptVersion", value.promptVersion());
    p.put("inputHash", value.inputHash());
    p.put("inputJson", value.inputJson());
    p.put("requestedBy", value.requestedBy());
    jdbc.update(
        """
        insert into work_record.wr_ai_generation(
          id, tenant_id, generation_type, resource_type, resource_id, period_start, period_end,
          prompt_version, input_hash, input_json, requested_by)
        values (:id, :tenantId, :type, :resourceType, :resourceId, :periodStart, :periodEnd,
                :promptVersion, :inputHash, cast(:inputJson as jsonb), :requestedBy)
        """,
        p);
    return find(value.tenantId(), value.id()).orElseThrow();
  }

  @Override
  public Optional<AiGeneration> find(String tenantId, String id) {
    return jdbc
        .query(
            select() + " where tenant_id=:tenantId and id=:id",
            Map.of("tenantId", tenantId, "id", id),
            this::map)
        .stream()
        .findFirst();
  }

  @Override
  public Optional<AiGeneration> findReusable(
      String tenantId, String type, String resourceType, String resourceId, String inputHash) {
    return jdbc
        .query(
            select()
                + " where tenant_id=:tenantId and generation_type=:type "
                + "and resource_type=:resourceType and resource_id=:resourceId and input_hash=:hash "
                + "and status in ('queued','running','success','accepted') order by created_at desc limit 1",
            Map.of(
                "tenantId",
                tenantId,
                "type",
                type,
                "resourceType",
                resourceType,
                "resourceId",
                resourceId,
                "hash",
                inputHash),
            this::map)
        .stream()
        .findFirst();
  }

  @Override
  public List<AiGeneration> listByResource(
      String tenantId, String resourceType, String resourceId) {
    return jdbc.query(
        select()
            + " where tenant_id=:tenantId and resource_type=:resourceType "
            + "and resource_id=:resourceId order by created_at desc",
        Map.of("tenantId", tenantId, "resourceType", resourceType, "resourceId", resourceId),
        this::map);
  }

  @Override
  public boolean markRunning(String tenantId, String id) {
    return updateState(new StateChange(tenantId, id, "running", "queued", null, null, null)) == 1;
  }

  @Override
  public boolean complete(
      String tenantId, String id, String markdown, String provider, String model) {
    return updateState(
            new StateChange(tenantId, id, "success", "running", markdown, provider, model))
        == 1;
  }

  @Override
  public boolean fail(String tenantId, String id) {
    return jdbc.update(
            "update work_record.wr_ai_generation set status='failed', finished_at=now() "
                + "where tenant_id=:tenantId and id=:id and status='running'",
            Map.of("tenantId", tenantId, "id", id))
        == 1;
  }

  @Override
  public boolean review(String tenantId, String id, String targetStatus, String reviewerId) {
    return jdbc.update(
            "update work_record.wr_ai_generation set status=:status, reviewed_by=:reviewer, "
                + "reviewed_at=now() where tenant_id=:tenantId and id=:id and status='success'",
            Map.of("status", targetStatus, "reviewer", reviewerId, "tenantId", tenantId, "id", id))
        == 1;
  }

  private int updateState(StateChange change) {
    Map<String, Object> p = new HashMap<>();
    p.put("tenantId", change.tenantId());
    p.put("id", change.id());
    p.put("target", change.target());
    p.put("expected", change.expected());
    p.put("markdown", change.markdown());
    p.put("provider", change.provider());
    p.put("model", change.model());
    return jdbc.update(
        "update work_record.wr_ai_generation set status=:target, "
            + "output_markdown=coalesce(:markdown,output_markdown), provider=coalesce(:provider,provider), "
            + "model=coalesce(:model,model), finished_at=case when :target='success' then now() else finished_at end "
            + "where tenant_id=:tenantId and id=:id and status=:expected",
        p);
  }

  private record StateChange(
      String tenantId,
      String id,
      String target,
      String expected,
      String markdown,
      String provider,
      String model) {}

  private AiGeneration map(ResultSet rs, int row) throws SQLException {
    return new AiGeneration(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("generation_type"),
        rs.getString("resource_type"),
        rs.getString("resource_id"),
        rs.getObject("period_start", LocalDate.class),
        rs.getObject("period_end", LocalDate.class),
        rs.getString("status"),
        rs.getString("prompt_version"),
        rs.getString("input_hash"),
        rs.getString("input_json"),
        rs.getString("output_markdown"),
        rs.getString("provider"),
        rs.getString("model"),
        rs.getString("requested_by"),
        rs.getString("reviewed_by"),
        rs.getObject("reviewed_at", OffsetDateTime.class),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("finished_at", OffsetDateTime.class));
  }

  private static String select() {
    return "select id,tenant_id,generation_type,resource_type,resource_id,period_start,period_end,"
        + "status,prompt_version,input_hash,input_json::text,output_markdown,provider,model,"
        + "requested_by,reviewed_by,reviewed_at,created_at,finished_at from work_record.wr_ai_generation";
  }
}
