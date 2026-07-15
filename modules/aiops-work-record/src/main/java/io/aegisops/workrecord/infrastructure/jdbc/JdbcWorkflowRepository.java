package io.aegisops.workrecord.infrastructure.jdbc;

import io.aegisops.common.id.Ids;
import io.aegisops.workrecord.application.port.WorkflowRepository;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkflowRepository implements WorkflowRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcWorkflowRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<ApprovalDefinition> findApprovalDefinition(String tenantId, String templateId) {
    return jdbc
        .query(
            """
            select d.id,s.approver_type,s.approver_value,s.timeout_minutes
            from work_record.wr_approval_definition d join work_record.wr_approval_step s
              on s.definition_id=d.id and s.step_no=1
            where d.tenant_id=:tenantId and d.template_id=:templateId
              and d.enabled=true and d.trigger_status='done'
            order by d.version_no desc limit 1
            """,
            Map.of("tenantId", tenantId, "templateId", templateId),
            (rs, row) ->
                new ApprovalDefinition(
                    rs.getString("id"),
                    rs.getString("approver_type"),
                    rs.getString("approver_value"),
                    (Integer) rs.getObject("timeout_minutes")))
        .stream()
        .findFirst();
  }

  @Override
  public void startApproval(
      String tenantId,
      String recordId,
      ApprovalDefinition definition,
      String actorId,
      OffsetDateTime dueAt) {
    String instanceId = Ids.newId();
    jdbc.update(
        "insert into work_record.wr_approval_instance(id,tenant_id,record_id,definition_id,started_by) "
            + "values(:id,:tenantId,:recordId,:definitionId,:actor)",
        Map.of(
            "id", instanceId,
            "tenantId", tenantId,
            "recordId", recordId,
            "definitionId", definition.id(),
            "actor", actorId));
    Map<String, Object> params = new HashMap<>();
    params.put("id", Ids.newId());
    params.put("tenantId", tenantId);
    params.put("instanceId", instanceId);
    params.put("type", definition.approverType());
    params.put("value", definition.approverValue());
    params.put("dueAt", dueAt);
    jdbc.update(
        "insert into work_record.wr_approval_task(id,tenant_id,instance_id,step_no,assignee_type,assignee_value,due_at) "
            + "values(:id,:tenantId,:instanceId,1,:type,:value,:dueAt)",
        params);
  }

  @Override
  public List<SlaPolicy> findSlaPolicies(String tenantId, String templateId) {
    return jdbc.query(
        "select id,start_event,stop_event,target_minutes,calendar_aware from work_record.wr_sla_policy "
            + "where tenant_id=:tenantId and template_id=:templateId and enabled=true",
        Map.of("tenantId", tenantId, "templateId", templateId),
        (rs, row) ->
            new SlaPolicy(
                rs.getString("id"),
                rs.getString("start_event"),
                rs.getString("stop_event"),
                rs.getInt("target_minutes"),
                rs.getBoolean("calendar_aware")));
  }

  @Override
  public void startSla(
      String tenantId,
      String recordId,
      String policyId,
      OffsetDateTime startedAt,
      OffsetDateTime dueAt) {
    jdbc.update(
        "insert into work_record.wr_sla_instance(id,tenant_id,record_id,policy_id,started_at,due_at) "
            + "values(:id,:tenantId,:recordId,:policyId,:startedAt,:dueAt) "
            + "on conflict(tenant_id,record_id,policy_id) do nothing",
        Map.of(
            "id", Ids.newId(),
            "tenantId", tenantId,
            "recordId", recordId,
            "policyId", policyId,
            "startedAt", startedAt,
            "dueAt", dueAt));
  }

  @Override
  public void stopSla(String tenantId, String recordId, String policyId) {
    jdbc.update(
        "update work_record.wr_sla_instance set status='met',stopped_at=now(), "
            + "elapsed_minutes=extract(epoch from(now()-started_at))::integer/60,row_version=row_version+1 "
            + "where tenant_id=:tenantId and record_id=:recordId and policy_id=:policyId and status='running'",
        Map.of("tenantId", tenantId, "recordId", recordId, "policyId", policyId));
  }

  @Override
  public Optional<ApprovalTask> lockPendingTask(String tenantId, String taskId) {
    return jdbc
        .query(
            """
            select t.instance_id,t.assignee_type,t.assignee_value,i.record_id,r.owner_id
            from work_record.wr_approval_task t
            join work_record.wr_approval_instance i on i.id=t.instance_id and i.tenant_id=t.tenant_id
            join work_record.wr_record r on r.id=i.record_id and r.tenant_id=i.tenant_id
            where t.tenant_id=:tenantId and t.id=:id and t.status='pending' for update
            """,
            Map.of("tenantId", tenantId, "id", taskId),
            (rs, row) ->
                new ApprovalTask(
                    rs.getString("instance_id"),
                    rs.getString("record_id"),
                    rs.getString("owner_id"),
                    rs.getString("assignee_type"),
                    rs.getString("assignee_value")))
        .stream()
        .findFirst();
  }

  @Override
  public List<ApprovalTaskView> listPendingTasks(
      String tenantId, String userId, Set<String> roles) {
    Set<String> safeRoles = roles == null || roles.isEmpty() ? Set.of("__none__") : roles;
    return jdbc.query(
        """
        select t.id,t.instance_id,i.record_id,r.title,t.assignee_type,t.assignee_value,
               t.due_at,t.created_at
        from work_record.wr_approval_task t
        join work_record.wr_approval_instance i on i.id=t.instance_id and i.tenant_id=t.tenant_id
        join work_record.wr_record r on r.id=i.record_id and r.tenant_id=i.tenant_id
        where t.tenant_id=:tenantId and t.status='pending' and (
          (t.assignee_type='user' and t.assignee_value=:userId) or
          (t.assignee_type='role' and t.assignee_value in (:roles)) or
          (t.assignee_type='record_owner' and r.owner_id=:userId))
        order by t.created_at desc limit 100
        """,
        Map.of("tenantId", tenantId, "userId", userId, "roles", safeRoles),
        (rs, row) ->
            new ApprovalTaskView(
                rs.getString("id"),
                rs.getString("instance_id"),
                rs.getString("record_id"),
                rs.getString("title"),
                rs.getString("assignee_type"),
                rs.getString("assignee_value"),
                rs.getObject("due_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class)));
  }

  @Override
  public List<SlaInstanceView> listSlaInstances(String tenantId, String recordId) {
    return jdbc.query(
        """
        select i.id,i.record_id,p.name policy_name,i.status,i.started_at,i.due_at,
               i.stopped_at,i.breached_at,p.severity
        from work_record.wr_sla_instance i join work_record.wr_sla_policy p
          on p.id=i.policy_id and p.tenant_id=i.tenant_id
        where i.tenant_id=:tenantId and i.record_id=:recordId
        order by i.started_at desc
        """,
        Map.of("tenantId", tenantId, "recordId", recordId),
        (rs, row) ->
            new SlaInstanceView(
                rs.getString("id"),
                rs.getString("record_id"),
                rs.getString("policy_name"),
                rs.getString("status"),
                rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("due_at", OffsetDateTime.class),
                rs.getObject("stopped_at", OffsetDateTime.class),
                rs.getObject("breached_at", OffsetDateTime.class),
                rs.getString("severity")));
  }

  @Override
  public void finishApproval(ApprovalAction action) {
    String status = action.approved() ? "approved" : "rejected";
    Map<String, Object> taskParams = new HashMap<>();
    taskParams.put("status", status);
    taskParams.put("actor", action.actorId());
    taskParams.put("comment", action.comment());
    taskParams.put("tenantId", action.tenantId());
    taskParams.put("id", action.taskId());
    jdbc.update(
        "update work_record.wr_approval_task set status=:status,acted_by=:actor,action_comment=:comment,acted_at=now() "
            + "where tenant_id=:tenantId and id=:id and status='pending'",
        taskParams);
    jdbc.update(
        "update work_record.wr_approval_instance set status=:status,finished_at=now(),row_version=row_version+1 "
            + "where tenant_id=:tenantId and id=:id and status='pending'",
        Map.of(
            "status", status,
            "tenantId", action.tenantId(),
            "id", action.instanceId()));
    jdbc.update(
        "update work_record.wr_record set status=:status,row_version=row_version+1,updated_at=now() "
            + "where tenant_id=:tenantId and id=:id and status='pending_approval'",
        Map.of(
            "status", action.approved() ? "done" : "rejected",
            "tenantId", action.tenantId(),
            "id", action.recordId()));
  }

  @Override
  public int markBreached(OffsetDateTime now, int limit) {
    List<String> ids =
        jdbc.queryForList(
            "select id from work_record.wr_sla_instance "
                + "where status='running' and due_at<=:now order by due_at limit :limit for update skip locked",
            Map.of("now", now, "limit", limit),
            String.class);
    int updated = 0;
    for (String id : ids) {
      updated +=
          jdbc.update(
              "update work_record.wr_sla_instance set status='breached',breached_at=:now,row_version=row_version+1 "
                  + "where id=:id and status='running'",
              Map.of("now", now, "id", id));
    }
    return updated;
  }

  @Override
  public int nextApprovalVersion(String tenantId, String templateId) {
    Integer value =
        jdbc.queryForObject(
            "select coalesce(max(version_no),0)+1 from work_record.wr_approval_definition "
                + "where tenant_id=:tenantId and template_id=:templateId",
            Map.of("tenantId", tenantId, "templateId", templateId),
            Integer.class);
    return value == null ? 1 : value;
  }

  @Override
  public void disableApprovals(String tenantId, String templateId) {
    jdbc.update(
        "update work_record.wr_approval_definition set enabled=false where tenant_id=:tenantId and template_id=:templateId",
        Map.of("tenantId", tenantId, "templateId", templateId));
  }

  @Override
  public void createApprovalDefinition(CreateApprovalDefinition command) {
    jdbc.update(
        "insert into work_record.wr_approval_definition(id,tenant_id,template_id,name,version_no,created_by) "
            + "values(:id,:tenantId,:templateId,:name,:version,:actor)",
        Map.of(
            "id", command.id(),
            "tenantId", command.tenantId(),
            "templateId", command.templateId(),
            "name", command.name(),
            "version", command.version(),
            "actor", command.actorId()));
  }

  @Override
  public void createApprovalStep(
      String id,
      String definitionId,
      String approverType,
      String approverValue,
      Integer timeoutMinutes) {
    Map<String, Object> params = new HashMap<>();
    params.put("id", id);
    params.put("definitionId", definitionId);
    params.put("type", approverType);
    params.put("value", approverValue);
    params.put("timeout", timeoutMinutes);
    jdbc.update(
        "insert into work_record.wr_approval_step(id,definition_id,step_no,name,approver_type,approver_value,timeout_minutes) "
            + "values(:id,:definitionId,1,'审批',:type,:value,:timeout)",
        params);
  }

  @Override
  public void createSlaPolicy(CreateSlaPolicy command) {
    jdbc.update(
        "insert into work_record.wr_sla_policy(id,tenant_id,template_id,name,start_event,stop_event,target_minutes,calendar_aware,severity,created_by) "
            + "values(:id,:tenantId,:templateId,:name,:start,:stop,:minutes,:calendarAware,:severity,:actor)",
        Map.of(
            "id", command.id(),
            "tenantId", command.tenantId(),
            "templateId", command.templateId(),
            "name", command.name(),
            "start", command.startEvent(),
            "stop", command.stopEvent(),
            "minutes", command.targetMinutes(),
            "calendarAware", command.calendarAware(),
            "severity", command.severity(),
            "actor", command.actorId()));
  }
}
