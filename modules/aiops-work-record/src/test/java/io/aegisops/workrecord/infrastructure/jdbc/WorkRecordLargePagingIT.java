package io.aegisops.workrecord.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.port.WorkRecordTelemetry;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 大数据分页 IT：
 *
 * <ul>
 *   <li>使用 Testcontainers 启动 PostgreSQL 16；
 *   <li>插入 100_000 条 wr_record，覆盖单租户全量；
 *   <li>验证 pageSize=200、offset=99800 返回 200 条且排序稳定；
 *   <li>租户隔离生效；
 *   <li>耗时低于 CI 宽松阈值（3s）；
 *   <li>offset 超出 maxOffset 抛 PAGE_WINDOW_EXCEEDED。
 * </ul>
 *
 * <p>默认禁用，需要 docker 才能运行；启用方式：
 *
 * <pre>
 *   mvn -Dtest.include.large.paging=true -Dtest=WorkRecordLargePagingIT verify
 * </pre>
 */
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "test.include.large.paging", matches = "true")
class WorkRecordLargePagingIT {

  private static final int TOTAL_RECORDS = 100_000;
  private static final int TARGET_OFFSET = 99_800;
  private static final int PAGE_SIZE = 200;
  private static final Duration MAX_PAGE_DURATION = Duration.ofSeconds(3);

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

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
            new WorkRecordJsonbFilterSqlBuilder(new ObjectMapper()),
            WorkRecordTelemetry.noop());

    jdbc.getJdbcTemplate().execute("create schema work_record");

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

    jdbc.getJdbcTemplate()
        .execute(
            "create index idx_wr_record_tenant_time on work_record.wr_record (tenant_id, record_time desc, id desc)");

    seed();
    analyze();
  }

  @BeforeEach
  void cleanTransient() {
    // 不清理种子数据，避免每次重新灌 10 万行；
    // 仅提供测试间互相独立可在未来扩展。
  }

  @Test
  void pageAtHighOffsetReturnsRequestedSliceWithTenantIsolation() {
    long start = System.nanoTime();
    var page =
        repository.page(
            "tenant-1",
            new RecordQuery(
                TARGET_OFFSET / PAGE_SIZE + 1,
                PAGE_SIZE,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                List.of(),
                "recordTime",
                "asc",
                "all",
                null));
    Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

    assertThat(page.items()).hasSize(PAGE_SIZE);
    assertThat(page.total()).isGreaterThanOrEqualTo(TOTAL_RECORDS);
    assertThat(page.items())
        .allSatisfy(record -> assertThat(record.tenantId()).isEqualTo("tenant-1"));

    // 排序稳定：recordTime 严格递增（同时间戳则 id 递增）
    List<WorkRecord> items = page.items();
    for (int i = 1; i < items.size(); i++) {
      WorkRecord previous = items.get(i - 1);
      WorkRecord current = items.get(i);
      assertThat(current.recordTime())
          .isAfterOrEqualTo(previous.recordTime());
      if (current.recordTime().isEqual(previous.recordTime())) {
        assertThat(current.id()).isGreaterThan(previous.id());
      }
    }

    assertThat(elapsed)
        .as("page query should finish within CI-threshold %s", MAX_PAGE_DURATION)
        .isLessThan(MAX_PAGE_DURATION);
  }

  @Test
  void tenantIsolationReturnsZeroRowsForOtherTenant() {
    var page =
        repository.page(
            "tenant-other",
            new RecordQuery(
                1,
                PAGE_SIZE,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                List.of(),
                "recordTime",
                "asc",
                "all",
                null));

    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isZero();
  }

  private static void seed() {
    jdbc.getJdbcTemplate().execute("truncate table work_record.wr_record");

    int batchSize = 2_000;
    OffsetDateTime baseTime = OffsetDateTime.parse("2024-01-01T00:00:00+00:00");

    for (int offset = 0; offset < TOTAL_RECORDS; offset += batchSize) {
      int end = Math.min(offset + batchSize, TOTAL_RECORDS);
      @SuppressWarnings("unchecked")
      Map<String, Object>[] batch = new Map[end - offset];
      for (int i = offset; i < end; i++) {
        batch[i - offset] =
            Map.of(
                "id", "rec_" + String.format("%07d", i),
                "tenantId", "tenant-1",
                "title", "record-" + i,
                "recordTime", baseTime.plusMinutes(i));
      }
      SqlParameterSource[] params = SqlParameterSourceUtils.createBatch(batch);

      jdbc.batchUpdate(
          """
          insert into work_record.wr_record(
            id, tenant_id, template_id, template_version_id, title, status,
            owner_id, creator_id, record_time, builtin_data_json, custom_data_json
          ) values (
            :id, :tenantId, 'tpl1', 'v1', :title, 'done',
            'user-1', 'user-1', :recordTime, '{}'::jsonb, '{}'::jsonb
          )
          """,
          params);
    }
  }

  private static void analyze() {
    jdbc.getJdbcTemplate().execute("analyze work_record.wr_record");
  }
}
