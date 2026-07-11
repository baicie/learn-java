package io.aegisops.workrecord.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.command.DynamicFilterOperator;
import io.aegisops.workrecord.application.command.RecordDynamicFilter;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.domain.model.FieldType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class JdbcWorkRecordRepositoryPostgresIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  private static NamedParameterJdbcTemplate jdbc;
  private static JdbcWorkRecordRepository repository;

  @BeforeAll
  static void initializeDatabase() {
    PGSimpleDataSource dataSource = new PGSimpleDataSource();
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUser(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());

    jdbc = new NamedParameterJdbcTemplate(dataSource);

    repository =
        new JdbcWorkRecordRepository(
            jdbc,
            new WorkRecordJsonbFilterSqlBuilder(new ObjectMapper()));

    jdbc.getJdbcTemplate().execute("create schema work_record");

    jdbc.getJdbcTemplate()
        .execute(
            """
            create or replace function work_record.try_numeric(input text)
            returns numeric
            language plpgsql
            immutable
            strict
            parallel safe
            as $$
            begin
              return input::numeric;
            exception when others then
              return null;
            end;
            $$
            """);

    jdbc.getJdbcTemplate()
        .execute(
            """
            create or replace function work_record.try_date(input text)
            returns date
            language plpgsql
            immutable
            strict
            parallel safe
            as $$
            begin
              return input::date;
            exception when others then
              return null;
            end;
            $$
            """);

    jdbc.getJdbcTemplate()
        .execute(
            """
            create or replace function work_record.try_timestamptz(input text)
            returns timestamptz
            language plpgsql
            stable
            strict
            parallel safe
            as $$
            begin
              return input::timestamptz;
            exception when others then
              return null;
            end;
            $$
            """);

    jdbc.getJdbcTemplate()
        .execute(
            """
            create table work_record.wr_record (
              id varchar(64) primary key,
              tenant_id varchar(64) not null,
              template_id varchar(64) not null,
              template_version_id varchar(64) not null,
              title varchar(200) not null,
              status varchar(32) not null,
              owner_id varchar(64),
              creator_id varchar(64) not null,
              record_time timestamptz not null,
              builtin_data_json jsonb not null default '{}'::jsonb,
              custom_data_json jsonb not null default '{}'::jsonb,
              row_version integer not null default 1,
              created_at timestamptz not null default now(),
              updated_at timestamptz not null default now(),
              deleted_at timestamptz
            )
            """);
  }

  @BeforeEach
  void cleanData() {
    jdbc.getJdbcTemplate().execute("truncate table work_record.wr_record");
  }

  @Test
  void containsAnyWithMultipleValuesMustExecuteAndRespectTenantIsolation() {
    insert("record-a", "tenant-1", "{\"tags\":[\"a\",\"b\"]}");
    insert("record-c", "tenant-1", "{\"tags\":[\"c\"]}");
    insert("other-tenant", "tenant-2", "{\"tags\":[\"b\"]}");

    RecordDynamicFilter filter =
        RecordDynamicFilter.normalized(
            "tags",
            DynamicFilterOperator.CONTAINS_ANY,
            FieldType.MULTI_SELECT,
            null,
            List.of("b", "x"));

    var page = repository.page("tenant-1", query(List.of(filter)));

    assertThat(page.items())
        .extracting(item -> item.id())
        .containsExactly("record-a");
  }

  @Test
  void containsAllMustMatchOnlyRowsContainingEveryValue() {
    insert("record-all", "tenant-1", "{\"tags\":[\"a\",\"b\",\"c\"]}");
    insert("record-one", "tenant-1", "{\"tags\":[\"a\"]}");

    RecordDynamicFilter filter =
        RecordDynamicFilter.normalized(
            "tags",
            DynamicFilterOperator.CONTAINS_ALL,
            FieldType.MULTI_SELECT,
            null,
            List.of("a", "b"));

    var page = repository.page("tenant-1", query(List.of(filter)));

    assertThat(page.items())
        .extracting(item -> item.id())
        .containsExactly("record-all");
  }

  @Test
  void numericQueryMustIgnoreHistoricalWrongJsonTypes() {
    insert("numeric", "tenant-1", "{\"cost\":12.5}");
    insert("string", "tenant-1", "{\"cost\":\"999\"}");
    insert("dirty", "tenant-1", "{\"cost\":\"not-number\"}");

    RecordDynamicFilter filter =
        RecordDynamicFilter.normalized(
            "cost",
            DynamicFilterOperator.GTE,
            FieldType.NUMBER,
            "10",
            List.of());

    var page = repository.page("tenant-1", query(List.of(filter)));

    assertThat(page.items())
        .extracting(item -> item.id())
        .containsExactly("numeric");
  }

  @Test
  void unsafeFieldCodeMustNeverReachJdbc() {
    RecordDynamicFilter filter =
        RecordDynamicFilter.normalized(
            "x') or true --",
            DynamicFilterOperator.EQ,
            FieldType.TEXT,
            "anything",
            List.of());

    assertThatThrownBy(() -> repository.page("tenant-1", query(List.of(filter))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid fieldCode");
  }

  private void insert(String id, String tenantId, String customJson) {
    jdbc.update(
        """
        insert into work_record.wr_record(
          id,
          tenant_id,
          template_id,
          template_version_id,
          title,
          status,
          owner_id,
          creator_id,
          record_time,
          builtin_data_json,
          custom_data_json
        )
        values (
          :id,
          :tenantId,
          'template-1',
          'version-1',
          :title,
          'done',
          'user-1',
          'user-1',
          :recordTime,
          '{}'::jsonb,
          cast(:custom as jsonb)
        )
        """,
        Map.of(
            "id", id,
            "tenantId", tenantId,
            "title", id,
            "recordTime", OffsetDateTime.parse("2026-07-11T10:00:00+08:00"),
            "custom", customJson));
  }

  private RecordQuery query(List<RecordDynamicFilter> filters) {
    return new RecordQuery(
        1,
        20,
        "template-1",
        null,
        List.of(),
        null,
        null,
        null,
        null,
        null,
        false,
        null,
        filters,
        "recordTime",
        "asc",
        "all",
        null);
  }
}
