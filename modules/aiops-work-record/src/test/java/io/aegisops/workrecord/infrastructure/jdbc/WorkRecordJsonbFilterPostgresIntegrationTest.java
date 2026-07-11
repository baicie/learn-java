package io.aegisops.workrecord.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.command.DynamicFilterOperator;
import io.aegisops.workrecord.application.command.RecordDynamicFilter;
import io.aegisops.workrecord.domain.model.FieldType;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class WorkRecordJsonbFilterPostgresIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static NamedParameterJdbcTemplate jdbc;
  static WorkRecordJsonbFilterSqlBuilder builder;

  @BeforeAll
  static void setUp() {
    PGSimpleDataSource dataSource = new PGSimpleDataSource();
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUser(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());

    jdbc = new NamedParameterJdbcTemplate(dataSource);
    builder = new WorkRecordJsonbFilterSqlBuilder(new ObjectMapper());

    jdbc.getJdbcTemplate().execute("create schema work_record");

    // Install safe cast functions (mirrors V0021 migration)
    jdbc.getJdbcTemplate()
        .execute(
            """
            create or replace function work_record.try_numeric(p_value text)
            returns numeric language plpgsql immutable strict parallel safe as $$
            begin return p_value::numeric;
            exception when others then return null;
            end; $$""");

    jdbc.getJdbcTemplate()
        .execute(
            """
            create or replace function work_record.try_date(p_value text)
            returns date language plpgsql immutable strict parallel safe as $$
            begin return p_value::date;
            exception when others then return null;
            end; $$""");

    jdbc.getJdbcTemplate()
        .execute(
            """
            create or replace function work_record.try_timestamptz(p_value text)
            returns timestamptz language plpgsql immutable strict parallel safe as $$
            begin return p_value::timestamptz;
            exception when others then return null;
            end; $$""");

    jdbc.getJdbcTemplate()
        .execute(
            """
            create table work_record.dynamic_query_test(
              id text primary key,
              custom_data_json jsonb not null
            )""");

    jdbc.getJdbcTemplate()
        .execute(
            """
            insert into work_record.dynamic_query_test(id, custom_data_json)
            values
              ('valid',
               '{"cost":12.5,
                 "day":"2026-01-01",
                 "startedAt":"2026-01-01T00:00:00Z",
                 "tags":["a","b"],
                 "priority":"P1"}'),
              ('dirty',
               '{"cost":"bad",
                 "day":"2026-99-99",
                 "startedAt":"not-a-date",
                 "tags":"not-array"}'),
              ('missing', '{}')
            """);
  }

  @Test
  void shouldExecuteExistsThroughPreparedStatement() {
    assertThat(
            executeCount(
                RecordDynamicFilter.normalized(
                    "priority", DynamicFilterOperator.EXISTS, FieldType.SELECT, null, List.of())))
        .isEqualTo(1);
  }

  @Test
  void shouldIgnoreDirtyNumericValuesInsteadOfFailingQuery() {
    // 'bad' cannot be cast to numeric; query should return the 'valid' row only.
    assertThat(
            executeCount(
                RecordDynamicFilter.normalized(
                    "cost", DynamicFilterOperator.GTE, FieldType.NUMBER, "10", List.of())))
        .isEqualTo(1);
  }

  @Test
  void shouldIgnoreDirtyDatetimeValuesInsteadOfFailingQuery() {
    // 'not-a-date' cannot be cast to timestamptz; query should return the 'valid' row only.
    assertThat(
            executeCount(
                RecordDynamicFilter.normalized(
                    "startedAt",
                    DynamicFilterOperator.LTE,
                    FieldType.DATETIME,
                    "2026-02-01T00:00:00Z",
                    List.of())))
        .isEqualTo(1);
  }

  @Test
  void shouldUseJsonbContainmentForMultiSelectWithoutScalarFailure() {
    // 'tags' is a scalar string in the dirty row; containment query should not fail.
    assertThat(
            executeCount(
                RecordDynamicFilter.normalized(
                    "tags",
                    DynamicFilterOperator.CONTAINS_ANY,
                    FieldType.MULTI_SELECT,
                    null,
                    List.of("b"))))
        .isEqualTo(1);
  }

  @Test
  void shouldGroupContainsAnyOrBranchesInsideAndParentheses() {
    // Two values means the SQL fragment must wrap the OR branches in an AND group,
    // so the fragment remains composable with the rest of the WHERE clause.
    StringBuilder where = new StringBuilder(" where true ");
    Map<String, Object> params = new HashMap<>();

    builder.appendFilters(
        where,
        params,
        List.of(
            RecordDynamicFilter.normalized(
                "tags",
                DynamicFilterOperator.CONTAINS_ANY,
                FieldType.MULTI_SELECT,
                null,
                List.of("a", "b"))));

    // The first filter branch already starts with " and ("; verify the closing paren
    // matches the OR-grouping contract rather than dangling inside the WHERE.
    String sql = where.toString();
    org.assertj.core.api.Assertions.assertThat(sql).contains(" and (");
    org.assertj.core.api.Assertions.assertThat(sql).contains(" or ");
    org.assertj.core.api.Assertions.assertThat(sql).endsWith(") ");

    // Both params must be present with stable keys.
    assertThat(params).containsKeys("dfKey0", "dfAny0_0", "dfAny0_1");
  }

  @Test
  void shouldMatchAnyOfMultipleValuesViaContainsAny() {
    // Row "valid" has tags ["a","b"]; contains_any ["a","x"] must match.
    assertThat(
            executeCount(
                RecordDynamicFilter.normalized(
                    "tags",
                    DynamicFilterOperator.CONTAINS_ANY,
                    FieldType.MULTI_SELECT,
                    null,
                    List.of("a", "x"))))
        .isEqualTo(1);
  }

  @Test
  void shouldRejectContainsAnyWhenBranchGroupingIsMissing() {
    // Defensive: this test guards against future regressions where a developer might
    // remove the wrapping " and (" / ") " pair around the OR-combined branches.
    StringBuilder where = new StringBuilder(" where true ");
    Map<String, Object> params = new HashMap<>();

    builder.appendFilters(
        where,
        params,
        List.of(
            RecordDynamicFilter.normalized(
                "tags",
                DynamicFilterOperator.CONTAINS_ANY,
                FieldType.MULTI_SELECT,
                null,
                List.of("a", "b"))));

    String sql = where.toString();

    // After " and (" there must be a "(jsonb_typeof(...)", then " or ", then a sibling branch.
    int groupStart = sql.indexOf(" and (");
    int firstBranch = sql.indexOf("(jsonb_typeof", groupStart);
    int orBranch = sql.indexOf(" or ", firstBranch);
    int lastParen = sql.lastIndexOf(')');

    org.assertj.core.api.Assertions.assertThat(groupStart).isNotEqualTo(-1);
    org.assertj.core.api.Assertions.assertThat(firstBranch).isGreaterThan(groupStart);
    org.assertj.core.api.Assertions.assertThat(orBranch).isGreaterThan(firstBranch);
    org.assertj.core.api.Assertions.assertThat(lastParen).isGreaterThan(orBranch);
  }

  @Test
  void shouldCompleteRepresentativeQueryWithinBound() {
    // A representative stress test: run 100 containment queries and verify they all finish
    // within a reasonable time bound (5s). This replaces the pure JVM string-concat benchmark.
    Executable query =
        () -> {
          for (int i = 0; i < 100; i++) {
            executeCount(
                RecordDynamicFilter.normalized(
                    "tags",
                    DynamicFilterOperator.CONTAINS_ALL,
                    FieldType.MULTI_SELECT,
                    null,
                    List.of("a", "b")));
          }
        };

    org.junit.jupiter.api.Assertions.assertTimeout(Duration.ofSeconds(5), query);
  }

  private long executeCount(RecordDynamicFilter filter) {
    StringBuilder where = new StringBuilder(" where true ");
    Map<String, Object> params = new HashMap<>();

    builder.appendFilters(where, params, List.of(filter));

    Long count =
        jdbc.queryForObject(
            "select count(*) from work_record.dynamic_query_test " + where, params, Long.class);

    return count == null ? 0L : count;
  }
}
