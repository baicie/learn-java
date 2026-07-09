package io.aegisops.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkRecordEnterpriseSchemaMigrationContractTest {

  private static final Path V0019 =
      Path.of("src/main/resources/db/migration/V0019__work_record_enterprise_schema.sql");

  private static final Path V0020 =
      Path.of(
          "src/main/resources/db/migration/V0020__harden_work_record_enterprise_constraints.sql");

  @Test
  void migrationFilesShouldExist() {
    assertThat(Files.exists(V0019)).isTrue();
    assertThat(Files.exists(V0020)).isTrue();
  }

  @Test
  void v0019ShouldCreateWorkRecordSchemaAndEnterpriseTables() throws Exception {
    String sql = Files.readString(V0019);

    assertThat(sql).contains("create schema if not exists work_record");
    assertThat(sql).contains("create table if not exists work_record.wr_template");
    assertThat(sql).contains("create table if not exists work_record.wr_template_version");
    assertThat(sql).contains("create table if not exists work_record.wr_template_field");
    assertThat(sql).contains("create table if not exists work_record.wr_record");
    assertThat(sql).contains("create table if not exists work_record.wr_record_snapshot");
    assertThat(sql).contains("create table if not exists work_record.wr_record_audit_event");
  }

  @Test
  void v0019ShouldBindRecordsToTemplateVersion() throws Exception {
    String sql = Files.readString(V0019);

    assertThat(sql).contains("template_version_id varchar(64) not null");
    assertThat(sql).contains("references work_record.wr_template_version(id)");
    assertThat(sql).contains("join work_record.wr_template_version v");
    assertThat(sql).contains("and v.version_no = 1");
  }

  @Test
  void v0019ShouldKeepFieldCodeImmutableAndValidated() throws Exception {
    String sql = Files.readString(V0019);

    assertThat(sql).contains("ck_wr_template_field_code");
    assertThat(sql).contains("^[a-zA-Z][a-zA-Z0-9_]{0,63}$");
    assertThat(sql).contains("work_record.reject_field_code_update");
    assertThat(sql).contains("trg_wr_template_field_code_immutable");
  }

  @Test
  void v0019ShouldHaveJsonbIndexesForDynamicQuery() throws Exception {
    String sql = Files.readString(V0019);

    assertThat(sql).contains("idx_wr_record_builtin_data_gin");
    assertThat(sql).contains("idx_wr_record_custom_data_gin");
    assertThat(sql).contains("using gin (builtin_data_json jsonb_path_ops)");
    assertThat(sql).contains("using gin (custom_data_json jsonb_path_ops)");
  }

  @Test
  void v0019ShouldMigrateOldPublicTablesWithoutDroppingThem() throws Exception {
    String sql = Files.readString(V0019);

    assertThat(sql).contains("from public.wr_template");
    assertThat(sql).contains("from public.wr_template_field");
    assertThat(sql).contains("from public.wr_record");
    assertThat(sql).doesNotContain("drop table public.wr_template");
    assertThat(sql).doesNotContain("drop table public.wr_template_field");
    assertThat(sql).doesNotContain("drop table public.wr_record");
  }

  @Test
  void v0020ShouldEnforceCompositeTenantConsistency() throws Exception {
    String sql = Files.readString(V0020);

    assertThat(sql).contains("uq_wr_template_tenant_id");
    assertThat(sql).contains("uq_wr_template_version_tenant_id");
    assertThat(sql).contains("uq_wr_template_version_tenant_template_id");

    assertThat(sql).contains("fk_wr_template_current_version_same_template");
    assertThat(sql).contains("foreign key (tenant_id, id, current_version_id)");

    assertThat(sql).contains("fk_wr_template_field_version_same_template");
    assertThat(sql).contains("foreign key (tenant_id, template_id, template_version_id)");

    assertThat(sql).contains("fk_wr_record_version_same_template");
    assertThat(sql).contains("foreign key (tenant_id, template_id, template_version_id)");

    assertThat(sql).contains("fk_wr_record_snapshot_version_same_template");
    assertThat(sql).contains("fk_wr_audit_record_same_tenant");
  }

  @Test
  void v0020ShouldForbidMorePhysicalDeletes() throws Exception {
    String sql = Files.readString(V0020);

    assertThat(sql).contains("trg_wr_template_no_delete");
    assertThat(sql).contains("trg_wr_template_version_no_delete");
    assertThat(sql).contains("trg_wr_record_snapshot_no_delete");
    assertThat(sql).contains("trg_wr_record_audit_event_no_delete");
  }

  @Test
  void v0020ShouldRebuildFieldIndexJson() throws Exception {
    String sql = Files.readString(V0020);

    assertThat(sql).contains("set field_index_json = coalesce");
    assertThat(sql).contains("jsonb_agg");
    assertThat(sql).contains("'fieldCode', f.field_code");
    assertThat(sql).contains("'exportable', f.exportable");
  }

  @Test
  void v0020ShouldNormalizeDirtyOptionSourceData() throws Exception {
    String sql = Files.readString(V0020);

    assertThat(sql).contains("option_source = 'static'");
    assertThat(sql).contains("option_source not in ('static', 'dict')");
    assertThat(sql).contains("option_source = 'dict' and (dict_code is null");
  }
}
