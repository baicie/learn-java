package io.aegisops.workrecord.infrastructure.jdbc;

import io.aegisops.common.id.Ids;
import io.aegisops.workrecord.application.port.RelationTargetPort;
import io.aegisops.workrecord.application.port.WorkRecordRelationRepository;
import io.aegisops.workrecord.domain.model.RelationType;
import io.aegisops.workrecord.domain.model.WorkRecordRelation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkRecordRelationRepository implements WorkRecordRelationRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcWorkRecordRelationRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<WorkRecordRelation> list(String tenantId, String recordId) {
    return jdbc.query(
        """
        select id, record_id, relation_type, target_id, target_title, target_status,
               snapshot_json::text, created_by, created_at
          from work_record.wr_record_relation
         where tenant_id = :tenantId and record_id = :recordId
         order by created_at desc, id
        """,
        Map.of("tenantId", tenantId, "recordId", recordId),
        this::map);
  }

  @Override
  public WorkRecordRelation create(
      String tenantId,
      String recordId,
      RelationType type,
      RelationTargetPort.ResolvedTarget target,
      String createdBy) {
    String id = Ids.newId();
    jdbc.update(
        """
        insert into work_record.wr_record_relation(
          id, tenant_id, record_id, relation_type, target_id, target_title,
          target_status, snapshot_json, created_by)
        values (:id, :tenantId, :recordId, :type, :targetId, :title,
                :status, cast(:snapshot as jsonb), :createdBy)
        """,
        Map.of(
            "id", id,
            "tenantId", tenantId,
            "recordId", recordId,
            "type", type.storageValue(),
            "targetId", target.id(),
            "title", target.title(),
            "status", target.status(),
            "snapshot", target.snapshotJson(),
            "createdBy", createdBy));
    return list(tenantId, recordId).stream()
        .filter(relation -> id.equals(relation.id()))
        .findFirst()
        .orElseThrow();
  }

  @Override
  public boolean delete(String tenantId, String recordId, String id) {
    return jdbc.update(
            """
            delete from work_record.wr_record_relation
             where tenant_id = :tenantId and record_id = :recordId and id = :id
            """,
            Map.of("tenantId", tenantId, "recordId", recordId, "id", id))
        == 1;
  }

  private WorkRecordRelation map(ResultSet rs, int rowNumber) throws SQLException {
    return new WorkRecordRelation(
        rs.getString("id"),
        rs.getString("record_id"),
        RelationType.from(rs.getString("relation_type")),
        rs.getString("target_id"),
        rs.getString("target_title"),
        rs.getString("target_status"),
        rs.getString("snapshot_json"),
        rs.getString("created_by"),
        rs.getObject("created_at", java.time.OffsetDateTime.class));
  }
}
