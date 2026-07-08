package io.aegisops.workrecord.infrastructure.config;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 首启动幂等 seed：把 §6.15.4 列出的默认工作记录模板写入每个 active 租户。
 *
 * <p>模板元数据放在 service 层执行是为了避免迁移期与 tenant FK 互相依赖；模板代码固定为 {@code default_daily_report} 和 {@code
 * default_change_record}，重复执行会跳过已存在模板。
 */
@Component
public class DefaultTemplateInitializer {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultTemplateInitializer.class);

  private static final String CODE_DAILY = "default_daily_report";
  private static final String NAME_DAILY = "日常巡检记录";
  private static final String CODE_CHANGE = "default_change_record";
  private static final String NAME_CHANGE = "变更执行记录";

  private final JdbcTemplate jdbc;

  public DefaultTemplateInitializer(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void seedDefaults() {
    List<String> tenants = activeTenants();
    if (tenants.isEmpty()) {
      LOG.info("default work-record template seed skipped: no active tenants");
      return;
    }
    int created = 0;
    for (String tenantId : tenants) {
      if (!templateExists(tenantId, CODE_DAILY)) {
        createTemplate(tenantId, CODE_DAILY, NAME_DAILY, dailyFields());
        created++;
      }
      if (!templateExists(tenantId, CODE_CHANGE)) {
        createTemplate(tenantId, CODE_CHANGE, NAME_CHANGE, changeFields());
        created++;
      }
    }
    LOG.info("default work-record template seed done: created={}", created);
  }

  private List<String> activeTenants() {
    return jdbc.query(
        "select id from tenant where status = 'active'", (rs, rowNum) -> rs.getString("id"));
  }

  private boolean templateExists(String tenantId, String code) {
    Integer count =
        jdbc.queryForObject(
            """
                select count(*) from wr_template
                 where tenant_id = ? and code = ?
                """,
            Integer.class,
            tenantId,
            code);
    return count != null && count > 0;
  }

  private void createTemplate(
      String tenantId, String code, String name, List<TemplateField> fields) {
    String templateId = UUID.randomUUID().toString();
    OffsetDateTime now = OffsetDateTime.now();
    jdbc.update(
        """
            insert into wr_template(
              id, tenant_id, name, code, description, enabled, schema_json,
              created_by, created_at, updated_at)
            values (?, ?, ?, ?, ?, true, '{}'::jsonb, 'system-bootstrap', ?, ?)
            """,
        templateId,
        tenantId,
        name,
        code,
        "系统默认模板",
        now,
        now);
    int order = 0;
    for (TemplateField field : fields) {
      jdbc.update(
          """
              insert into wr_template_field(
                id, tenant_id, template_id, field_name, field_code, field_type,
                required, default_value, option_source, dict_code, options_json,
                list_visible, filterable, exportable, statistical, sort_order, enabled,
                created_at, updated_at)
              values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb,
                      ?, ?, ?, ?, ?, true, ?, ?)
              """,
          UUID.randomUUID().toString(),
          tenantId,
          templateId,
          field.label(),
          field.code(),
          field.type(),
          field.required(),
          field.defaultValue(),
          field.optionSource(),
          field.dictCode(),
          field.optionsJson(),
          field.listVisible(),
          field.filterable(),
          field.exportable(),
          field.statistical(),
          order++,
          now,
          now);
    }
  }

  private List<TemplateField> dailyFields() {
    List<TemplateField> fields = new ArrayList<>();
    fields.add(
        new TemplateField(
            "环境", "env", "select", true, null, "dict", "env_type", "[]", true, true, true, false));
    fields.add(
        new TemplateField(
            "记录时间",
            "inspection_time",
            "datetime",
            true,
            null,
            "static",
            null,
            "[]",
            false,
            true,
            true,
            false));
    fields.add(
        new TemplateField(
            "摘要", "summary", "text", true, null, "static", null, "[]", true, true, true, false));
    fields.add(
        new TemplateField(
            "详情",
            "detail",
            "textarea",
            false,
            null,
            "static",
            null,
            "[]",
            false,
            false,
            true,
            false));
    fields.add(
        new TemplateField(
            "处理结果",
            "result",
            "select",
            false,
            null,
            "dict",
            "process_result",
            "[]",
            true,
            true,
            true,
            true));
    return fields;
  }

  private List<TemplateField> changeFields() {
    List<TemplateField> fields = new ArrayList<>();
    fields.add(
        new TemplateField(
            "变更编号",
            "change_id",
            "text",
            true,
            null,
            "static",
            null,
            "[]",
            true,
            true,
            true,
            false));
    fields.add(
        new TemplateField(
            "环境", "env", "select", true, null, "dict", "env_type", "[]", true, true, true, false));
    fields.add(
        new TemplateField(
            "变更窗口",
            "change_window",
            "datetime",
            true,
            null,
            "static",
            null,
            "[]",
            true,
            true,
            true,
            false));
    fields.add(
        new TemplateField(
            "影响范围",
            "impact",
            "textarea",
            true,
            null,
            "static",
            null,
            "[]",
            true,
            false,
            true,
            false));
    fields.add(
        new TemplateField(
            "是否回滚",
            "rolled_back",
            "switch",
            false,
            "false",
            "static",
            null,
            "[]",
            true,
            true,
            true,
            true));
    return fields;
  }

  private record TemplateField(
      String label,
      String code,
      String type,
      boolean required,
      String defaultValue,
      String optionSource,
      String dictCode,
      String optionsJson,
      boolean listVisible,
      boolean filterable,
      boolean exportable,
      boolean statistical) {}
}
