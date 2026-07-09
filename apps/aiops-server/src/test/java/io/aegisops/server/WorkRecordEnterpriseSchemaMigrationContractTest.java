package io.aegisops.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkRecordEnterpriseSchemaMigrationContractTest {

  private static final Path MIGRATION =
      Path.of("src/main/resources/db/migration/V0019__work_record_enterprise_schema.sql");

  @Test
  void migrationFileShouldExist() {
    assertThat(Files.exists(MIGRATION)).isTrue();
  }

  @Test
  void migrationShouldCreateWorkRecordSchemaAndEnterpriseTables() throws Exception {
    String sql = Files.readString(MIGRATION);

    assertThat(sql).contains("create schema if not exists work_record");

    assertThat(sql).contains("create table if not exists work_record.wr_template");
    assertThat(sql).contains("create table if not exists work_record.wr_template_version");
    assertThat(sql).contains("create table if not exists work_record.wr_template_field");
    assertThat(sql).contains("create table if not exists work_record.wr_record");
    assertThat(sql).contains("create table if not exists work_record.wr_record_snapshot");
    assertThat(sql).contains("create table if not exists work_record.wr_record_audit_event");
  }

  @Test
  void migrationShouldBindRecordsToTemplateVersion() throws Exception {
    String sql = Files.readString(MIGRATION);

    assertThat(sql).contains("template_version_id varchar(64) not null");
    assertThat(sql).contains("references work_record.wr_template_version(id)");
    assertThat(sql).contains("join work_record.wr_template_version v");
    assertThat(sql).contains("and v.version_no = 1");
  }

  @Test
  void migrationShouldKeepFieldCodeImmutableAndValidated() throws Exception {
    String sql = Files.readString(MIGRATION);

    assertThat(sql).contains("ck_wr_template_field_code");
    assertThat(sql).contains("^[a-zA-Z][a-zA-Z0-9_]{0,63}$");
    assertThat(sql).contains("work_record.reject_field_code_update");
    assertThat(sql).contains("trg_wr_template_field_code_immutable");
  }

  @Test
  void migrationShouldUseSoftDeleteSemantics() throws Exception {
    String sql = Files.readString(MIGRATION);

    assertThat(sql).contains("deleted_at timestamptz");
    assertThat(sql).contains("enabled boolean not null default true");
    assertThat(sql).contains("work_record.reject_physical_delete");
    assertThat(sql).contains("trg_wr_record_no_delete");
    assertThat(sql).contains("trg_wr_template_field_no_delete");
    assertThat(sql).contains("public.reject_platform_dict_item_delete");
  }

  @Test
  void migrationShouldHaveJsonbIndexesForDynamicQuery() throws Exception {
    String sql = Files.readString(MIGRATION);

    assertThat(sql).contains("idx_wr_record_builtin_data_gin");
    assertThat(sql).contains("idx_wr_record_custom_data_gin");
    assertThat(sql).contains("using gin (builtin_data_json jsonb_path_ops)");
    assertThat(sql).contains("using gin (custom_data_json jsonb_path_ops)");
  }

  @Test
  void migrationShouldMigrateOldPublicTablesWithoutDroppingThem() throws Exception {
    String sql = Files.readString(MIGRATION);

    assertThat(sql).contains("from public.wr_template");
    assertThat(sql).contains("from public.wr_template_field");
    assertThat(sql).contains("from public.wr_record");
    assertThat(sql).doesNotContain("drop table public.wr_template");
    assertThat(sql).doesNotContain("drop table public.wr_template_field");
    assertThat(sql).doesNotContain("drop table public.wr_record");
  }

  @Test
  void migrationShouldCreateInitialSnapshotsAndAuditEvents() throws Exception {
    String sql = Files.readString(MIGRATION);

    assertThat(sql).contains("insert into work_record.wr_record_snapshot");
    assertThat(sql).contains("'migration'");
    assertThat(sql).contains("insert into work_record.wr_record_audit_event");
    assertThat(sql).contains("'work_record.record.migrated'");
  }
}
