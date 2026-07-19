package io.aegisops.server.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 健康检查必须识别 {@code indisvalid=false / indisready=false} 的索引以及 {@code convalidated=false}
 * 的约束；否则会在生产中给出错误 UP。
 */
class WorkRecordHealthIndicatorTest {

  @Test
  void requiresIndexesButAllValidReturnsUp() {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    when(jdbc.queryForObject(eq("select 1"), any(Map.class), eq(Integer.class))).thenReturn(1);
    when(jdbc.queryForObject(any(String.class), any(Map.class), eq(Boolean.class)))
        .thenReturn(true);

    HealthIndicatorFixture fixture = newFixture(jdbc, true, false);

    Health health = fixture.indicator().health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).containsEntry("indexesRequired", true);
    assertThat(health.getDetails()).containsEntry("constraintsRequired", false);
  }

  @Test
  void missingOrInvalidIndexReturnsDownWithDetails() {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    when(jdbc.queryForObject(eq("select 1"), any(Map.class), eq(Integer.class))).thenReturn(1);
    when(jdbc.queryForObject(any(String.class), any(Map.class), eq(Boolean.class)))
        .thenAnswer(invocation -> invocation.<String>getArgument(0).contains("to_regclass"));

    HealthIndicatorFixture fixture = newFixture(jdbc, true, false);

    Health health = fixture.indicator().health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails().get("missingOrInvalidIndexes"))
        .isInstanceOf(java.util.List.class);
    assertThat((java.util.List<?>) health.getDetails().get("missingOrInvalidIndexes")).isNotEmpty();
  }

  @Test
  void requiresConstraintsButNotValidatedReturnsDown() {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    when(jdbc.queryForObject(eq("select 1"), any(Map.class), eq(Integer.class))).thenReturn(1);
    when(jdbc.queryForObject(any(String.class), any(Map.class), eq(Boolean.class)))
        .thenAnswer(invocation -> invocation.<String>getArgument(0).contains("to_regclass"));

    HealthIndicatorFixture fixture = newFixture(jdbc, false, true);

    Health health = fixture.indicator().health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails().get("unvalidatedConstraints"))
        .isInstanceOf(java.util.List.class);
  }

  @Test
  void noRequirementMeansNoIndexOrConstraintCheck() {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    when(jdbc.queryForObject(eq("select 1"), any(Map.class), eq(Integer.class))).thenReturn(1);
    when(jdbc.queryForObject(any(String.class), any(Map.class), eq(Boolean.class)))
        .thenReturn(true);

    HealthIndicatorFixture fixture = newFixture(jdbc, false, false);

    Health health = fixture.indicator().health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).containsEntry("indexesRequired", false);
    assertThat(health.getDetails()).containsEntry("constraintsRequired", false);
  }

  @Test
  void missingTableReturnsDown() {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    when(jdbc.queryForObject(eq("select 1"), any(Map.class), eq(Integer.class))).thenReturn(1);
    when(jdbc.queryForObject(
            org.mockito.ArgumentMatchers.contains("to_regclass"),
            any(Map.class),
            eq(Boolean.class)))
        .thenReturn(false);

    HealthIndicatorFixture fixture = newFixture(jdbc, false, false);

    Health health = fixture.indicator().health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails().get("missingTables")).isInstanceOf(java.util.List.class);
    assertThat((java.util.List<?>) health.getDetails().get("missingTables")).isNotEmpty();
  }

  @Test
  void databaseExceptionReturnsDown() {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    when(jdbc.queryForObject(eq("select 1"), any(Map.class), eq(Integer.class)))
        .thenThrow(new RuntimeException("connection refused"));

    HealthIndicatorFixture fixture = newFixture(jdbc, true, true);

    Health health = fixture.indicator().health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
  }

  private HealthIndicatorFixture newFixture(
      NamedParameterJdbcTemplate jdbc, boolean requireIndexes, boolean requireValidated) {
    return new HealthIndicatorFixture(jdbc, requireIndexes, requireValidated);
  }

  /** 简单的 Holder，让调用方少写一次构造。 */
  private record HealthIndicatorFixture(
      NamedParameterJdbcTemplate jdbc, boolean requireIndexes, boolean requireValidated) {

    WorkRecordHealthIndicator indicator() {
      return new WorkRecordHealthIndicator(jdbc, requireIndexes, requireValidated);
    }
  }

  // 显式保留 RowMapper 引用以满足静态分析工具在未使用类型时的告警抑制。
  @SuppressWarnings("unused")
  private static final Class<?> ROW_MAPPER_TYPE = RowMapper.class;
}
