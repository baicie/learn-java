package io.aegisops.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Table;
import org.jooq.impl.DSL;

/**
 * no-codegen jOOQ 表与字段常量。
 *
 * <p>Phase4.5 不做 codegen，先用 {@link DSL#table(org.jooq.Name)} + 别名 + 字段工厂写最小可用的表/字段引用。 Phase4.6 再用
 * jOOQ codegen 替换为生成的 Tables。
 */
public final class AegisTables {
  private AegisTables() {}

  public static final Table<?> INCIDENT = DSL.table(DSL.name("incident")).as("i");
  public static final Table<?> INCIDENT_EVENT = DSL.table(DSL.name("incident_event")).as("ie");
  public static final Table<?> ALERT_EVENT = DSL.table(DSL.name("alert_event")).as("a");
  public static final Table<?> RCA_ANALYSIS = DSL.table(DSL.name("rca_analysis")).as("r");
  public static final Table<?> ASSET_RELATION = DSL.table(DSL.name("asset_relation")).as("ar");
  public static final Table<?> AI_DIAGNOSIS = DSL.table(DSL.name("ai_diagnosis")).as("ad");
  public static final Table<?> INCIDENT_TIMELINE =
      DSL.table(DSL.name("incident_timeline")).as("it");
  public static final Table<?> AGENT_RUN = DSL.table(DSL.name("agent_run")).as("agr");
  public static final Table<?> AGENT_RUN_STEP = DSL.table(DSL.name("agent_run_step")).as("agrs");
  public static final Table<?> AGENT_EVAL_RESULT =
      DSL.table(DSL.name("agent_eval_result")).as("aer");
  public static final Table<?> LOG_EVENT = DSL.table(DSL.name("log_event")).as("le");
  public static final Table<?> CHANGE_EVENT = DSL.table(DSL.name("change_event")).as("ce");

  public static Field<String> str(Table<?> table, String column) {
    return DSL.field(DSL.name(table.getName(), column), String.class);
  }

  public static Field<Integer> integer(Table<?> table, String column) {
    return DSL.field(DSL.name(table.getName(), column), Integer.class);
  }

  public static Field<Long> lng(Table<?> table, String column) {
    return DSL.field(DSL.name(table.getName(), column), Long.class);
  }

  public static Field<Boolean> bool(Table<?> table, String column) {
    return DSL.field(DSL.name(table.getName(), column), Boolean.class);
  }

  public static Field<BigDecimal> decimal(Table<?> table, String column) {
    return DSL.field(DSL.name(table.getName(), column), BigDecimal.class);
  }

  public static Field<OffsetDateTime> time(Table<?> table, String column) {
    return DSL.field(DSL.name(table.getName(), column), OffsetDateTime.class);
  }

  public static Field<JSONB> jsonb(Table<?> table, String column) {
    return DSL.field(DSL.name(table.getName(), column), JSONB.class);
  }
}
