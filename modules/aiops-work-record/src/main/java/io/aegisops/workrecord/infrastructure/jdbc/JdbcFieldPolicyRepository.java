package io.aegisops.workrecord.infrastructure.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.id.Ids;
import io.aegisops.workrecord.application.port.FieldPolicyRepository;
import io.aegisops.workrecord.domain.model.FieldPolicy;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcFieldPolicyRepository implements FieldPolicyRepository {
  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public JdbcFieldPolicyRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Override
  public void replaceForVersion(String tenantId, String versionId, List<FieldPolicy> policies) {
    jdbc.update(
        "delete from work_record.wr_field_policy where tenant_id=:tenantId "
            + "and template_version_id=:versionId",
        Map.of("tenantId", tenantId, "versionId", versionId));
    for (FieldPolicy policy : policies) {
      jdbc.update(
          """
          insert into work_record.wr_field_policy(
            id,tenant_id,template_version_id,field_code,read_roles_json,write_roles_json,mask_mode)
          values (:id,:tenantId,:versionId,:fieldCode,cast(:readRoles as jsonb),
                  cast(:writeRoles as jsonb),:maskMode)
          """,
          Map.of(
              "id",
              Ids.newId(),
              "tenantId",
              tenantId,
              "versionId",
              versionId,
              "fieldCode",
              policy.fieldCode(),
              "readRoles",
              write(policy.readRoles()),
              "writeRoles",
              write(policy.writeRoles()),
              "maskMode",
              policy.maskMode().value()));
    }
  }

  @Override
  public List<FieldPolicy> listByVersion(String tenantId, String versionId) {
    return jdbc.query(
        """
        select template_version_id,field_code,read_roles_json::text,write_roles_json::text,mask_mode
          from work_record.wr_field_policy
         where tenant_id=:tenantId and template_version_id=:versionId order by field_code
        """,
        Map.of("tenantId", tenantId, "versionId", versionId),
        (rs, row) ->
            new FieldPolicy(
                rs.getString("template_version_id"),
                rs.getString("field_code"),
                read(rs.getString("read_roles_json")),
                read(rs.getString("write_roles_json")),
                FieldPolicy.MaskMode.from(rs.getString("mask_mode"))));
  }

  private String write(List<String> values) {
    try {
      return objectMapper.writeValueAsString(values);
    } catch (Exception ex) {
      throw new IllegalStateException("cannot serialize field policy", ex);
    }
  }

  private List<String> read(String value) {
    try {
      return objectMapper.readValue(value, new TypeReference<List<String>>() {});
    } catch (Exception ex) {
      throw new IllegalStateException("invalid field policy", ex);
    }
  }
}
