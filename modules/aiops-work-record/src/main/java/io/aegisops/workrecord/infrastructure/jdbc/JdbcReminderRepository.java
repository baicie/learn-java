package io.aegisops.workrecord.infrastructure.jdbc;

import io.aegisops.common.id.Ids;
import io.aegisops.workrecord.application.port.ReminderRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReminderRepository implements ReminderRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcReminderRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public ReminderRuleView create(CreateRule command) {
    String id = Ids.newId();
    jdbc.update(
        """
        insert into work_record.wr_reminder_rule(
          id, tenant_id, name, template_id, target_type, target_json,
          cutoff_time, time_zone, created_by)
        values (:id, :tenantId, :name, :templateId, :targetType, cast(:targetJson as jsonb),
                :cutoffTime, :timeZone, :actorId)
        """,
        Map.of(
            "id",
            id,
            "tenantId",
            command.tenantId(),
            "name",
            command.name(),
            "templateId",
            command.templateId(),
            "targetType",
            command.targetType(),
            "targetJson",
            command.targetJson(),
            "cutoffTime",
            command.cutoffTime(),
            "timeZone",
            command.timeZone(),
            "actorId",
            command.actorId()));
    return list(command.tenantId()).stream()
        .filter(value -> value.id().equals(id))
        .findFirst()
        .orElseThrow();
  }

  @Override
  public List<ReminderRuleView> list(String tenantId) {
    return jdbc.query(
        """
        select id, name, template_id, target_type, target_json::text,
               cutoff_time, time_zone, enabled
          from work_record.wr_reminder_rule
         where tenant_id = :tenantId order by created_at desc, id desc
        """,
        Map.of("tenantId", tenantId),
        (rs, rowNum) ->
            new ReminderRuleView(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("template_id"),
                rs.getString("target_type"),
                rs.getString("target_json"),
                rs.getObject("cutoff_time", java.time.LocalTime.class),
                rs.getString("time_zone"),
                rs.getBoolean("enabled")));
  }

  @Override
  public boolean setEnabled(String tenantId, String ruleId, boolean enabled) {
    return jdbc.update(
            """
        update work_record.wr_reminder_rule set enabled = :enabled, updated_at = now()
         where tenant_id = :tenantId and id = :id
        """,
            Map.of("enabled", enabled, "tenantId", tenantId, "id", ruleId))
        == 1;
  }

  @Override
  public List<ReminderRule> findDueRules(Instant now, int limit) {
    return jdbc.query(
        """
        select id, tenant_id, template_id, target_type, target_json::text,
               cutoff_time, time_zone
          from work_record.wr_reminder_rule
         where enabled = true
           and (:now at time zone time_zone)::time >= cutoff_time
         order by cutoff_time, id limit :limit
        """,
        Map.of("now", OffsetDateTime.ofInstant(now, ZoneId.of("UTC")), "limit", limit),
        (rs, rowNum) ->
            new ReminderRule(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("template_id"),
                rs.getString("target_type"),
                rs.getString("target_json"),
                rs.getObject("cutoff_time", java.time.LocalTime.class),
                rs.getString("time_zone")));
  }

  @Override
  public boolean hasRecord(
      String tenantId, String templateId, String userId, LocalDate date, ZoneId zoneId) {
    OffsetDateTime start = date.atStartOfDay(zoneId).toOffsetDateTime();
    OffsetDateTime end = date.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime();
    Long count =
        jdbc.queryForObject(
            """
        select count(*) from work_record.wr_record
         where tenant_id = :tenantId and template_id = :templateId and deleted_at is null
           and coalesce(owner_id, creator_id) = :userId
           and record_time >= :start and record_time < :end
        """,
            Map.of(
                "tenantId",
                tenantId,
                "templateId",
                templateId,
                "userId",
                userId,
                "start",
                start,
                "end",
                end),
            Long.class);
    return count != null && count > 0;
  }

  @Override
  public List<String> usersByRole(String tenantId, String roleCode) {
    return jdbc.queryForList(
        """
        select distinct ur.user_id
          from iam.user_role ur
          join public.sys_user u on u.id = ur.user_id and u.tenant_id = ur.tenant_id
         where ur.tenant_id = :tenantId and ur.role_code = :roleCode
           and u.status = 'active'
         order by ur.user_id
        """,
        Map.of("tenantId", tenantId, "roleCode", roleCode),
        String.class);
  }
}
