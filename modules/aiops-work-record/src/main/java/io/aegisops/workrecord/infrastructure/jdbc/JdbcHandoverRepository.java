package io.aegisops.workrecord.infrastructure.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.id.Ids;
import io.aegisops.workrecord.application.port.HandoverRepository;
import io.aegisops.workrecord.application.service.WorkRecordHandoverService.CreateHandoverCommand;
import io.aegisops.workrecord.domain.model.HandoverStatus;
import io.aegisops.workrecord.domain.model.WorkRecordHandover;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcHandoverRepository implements HandoverRepository {
  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public JdbcHandoverRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Override
  public WorkRecordHandover create(String tenantId, CreateHandoverCommand command, String actorId) {
    String id = Ids.newId();
    Map<String, Object> params = new HashMap<>();
    params.put("id", id);
    params.put("tenantId", tenantId);
    params.put("fromUserId", command.fromUserId());
    params.put("toUserId", command.toUserId());
    params.put("shiftStart", command.shiftStart());
    params.put("shiftEnd", command.shiftEnd());
    params.put("summary", command.summary().trim());
    params.put("recordIds", write(command.recordIds()));
    params.put("relationIds", write(command.relationIds()));
    params.put("actorId", actorId);
    jdbc.update(
        """
        insert into work_record.wr_handover(
          id, tenant_id, from_user_id, to_user_id, shift_start, shift_end,
          status, summary, record_ids_json, relation_ids_json, created_by)
        values (:id, :tenantId, :fromUserId, :toUserId, :shiftStart, :shiftEnd,
                'draft', :summary, cast(:recordIds as jsonb), cast(:relationIds as jsonb), :actorId)
        """,
        params);
    return find(tenantId, id).orElseThrow();
  }

  @Override
  public Optional<WorkRecordHandover> find(String tenantId, String handoverId) {
    return jdbc
        .query(
            selectSql() + " where tenant_id = :tenantId and id = :id",
            Map.of("tenantId", tenantId, "id", handoverId),
            this::map)
        .stream()
        .findFirst();
  }

  @Override
  public List<WorkRecordHandover> listForUser(String tenantId, String userId, int limit) {
    return jdbc.query(
        selectSql()
            + " where tenant_id = :tenantId "
            + "and (from_user_id = :userId or to_user_id = :userId) "
            + "order by created_at desc, id desc limit :limit",
        Map.of("tenantId", tenantId, "userId", userId, "limit", limit),
        this::map);
  }

  @Override
  public boolean transition(
      String tenantId,
      String handoverId,
      HandoverStatus expected,
      HandoverStatus target,
      int expectedVersion) {
    return jdbc.update(
            """
        update work_record.wr_handover
           set status = :target,
               accepted_at = case when :target = 'accepted' then now() else accepted_at end,
               completed_at = case when :target = 'completed' then now() else completed_at end,
               row_version = row_version + 1
         where tenant_id = :tenantId and id = :id
           and status = :expected and row_version = :version
        """,
            Map.of(
                "target",
                target.value(),
                "tenantId",
                tenantId,
                "id",
                handoverId,
                "expected",
                expected.value(),
                "version",
                expectedVersion))
        == 1;
  }

  private WorkRecordHandover map(ResultSet rs, int rowNum) throws SQLException {
    return new WorkRecordHandover(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("from_user_id"),
        rs.getString("to_user_id"),
        rs.getObject("shift_start", OffsetDateTime.class),
        rs.getObject("shift_end", OffsetDateTime.class),
        HandoverStatus.from(rs.getString("status")),
        rs.getString("summary"),
        readList(rs.getString("record_ids_json")),
        readList(rs.getString("relation_ids_json")),
        rs.getString("created_by"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("accepted_at", OffsetDateTime.class),
        rs.getObject("completed_at", OffsetDateTime.class),
        rs.getInt("row_version"));
  }

  private String write(List<String> values) {
    try {
      return objectMapper.writeValueAsString(values);
    } catch (Exception ex) {
      throw new IllegalStateException("cannot serialize handover links", ex);
    }
  }

  private List<String> readList(String value) {
    try {
      return objectMapper.readValue(value, new TypeReference<List<String>>() {});
    } catch (Exception ex) {
      throw new IllegalStateException("invalid handover links", ex);
    }
  }

  private static String selectSql() {
    return "select id, tenant_id, from_user_id, to_user_id, shift_start, shift_end, "
        + "status, summary, record_ids_json::text, relation_ids_json::text, created_by, "
        + "created_at, accepted_at, completed_at, row_version from work_record.wr_handover";
  }
}
