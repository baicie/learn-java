package io.aegisops.platform.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class DefaultCalendarRepositoryPostgresIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static NamedParameterJdbcTemplate jdbc;
  static DefaultCalendarRepository repository;

  @BeforeAll
  static void setUp() {
    PGSimpleDataSource dataSource = new PGSimpleDataSource();
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUser(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());

    jdbc = new NamedParameterJdbcTemplate(dataSource);
    repository = new DefaultCalendarRepository(jdbc);

    jdbc.getJdbcTemplate()
        .execute(
            """
            create table platform_calendar (
              id varchar(64) primary key,
              tenant_id varchar(64) not null,
              calendar_code varchar(64) not null,
              calendar_name varchar(128) not null,
              region_code varchar(32) not null,
              timezone varchar(64) not null,
              year integer not null,
              enabled boolean not null,
              source_type varchar(32) not null,
              description text,
              created_by varchar(64),
              created_at timestamptz not null default now(),
              updated_at timestamptz not null default now(),
              unique(id, tenant_id, year)
            )
            """);

    jdbc.getJdbcTemplate()
        .execute(
            """
            create table platform_calendar_day (
              id varchar(64) primary key,
              tenant_id varchar(64) not null,
              calendar_id varchar(64) not null,
              calendar_date date not null,
              day_of_week integer not null,
              day_type varchar(32) not null,
              is_workday boolean not null,
              holiday_code varchar(64),
              holiday_name varchar(128),
              source_type varchar(32) not null,
              remark text,
              created_by varchar(64),
              created_at timestamptz default now(),
              updated_at timestamptz default now(),
              unique(tenant_id, calendar_id, calendar_date)
            )
            """);

    jdbc.getJdbcTemplate()
        .execute(
            """
            create table platform_calendar_binding (
              tenant_id varchar(64) not null,
              calendar_year integer not null,
              calendar_id varchar(64) not null,
              created_by varchar(64) not null,
              updated_by varchar(64) not null,
              created_at timestamptz default now(),
              updated_at timestamptz default now(),
              primary key(tenant_id, calendar_year),
              foreign key(calendar_id, tenant_id, calendar_year)
                references platform_calendar(id, tenant_id, year)
            )
            """);

    insertCalendar("cal-2025", "t1", 2025);
    insertCalendar("cal-2026", "t1", 2026);
    insertCalendar("cal-other", "t2", 2026);

    bind("t1", 2025, "cal-2025");
    bind("t1", 2026, "cal-2026");
    bind("t2", 2026, "cal-other");

    insertDay("t1", "cal-2025", LocalDate.of(2025, 12, 30), "WORKDAY", true);
    insertDay("t1", "cal-2025", LocalDate.of(2025, 12, 31), "WORKDAY", true);
    insertDay("t1", "cal-2026", LocalDate.of(2026, 1, 1), "HOLIDAY", false);
    insertDay("t1", "cal-2026", LocalDate.of(2026, 1, 2), "WORKDAY", true);
    insertDay("t1", "cal-2026", LocalDate.of(2026, 1, 3), "ADJUSTED_WORKDAY", true);
    insertDay("t2", "cal-other", LocalDate.of(2026, 1, 3), "WORKDAY", true);
  }

  @Test
  void recentWorkdaysShouldCrossYearAndIncludeAdjustment() {
    List<LocalDate> result = repository.listRecentWorkdays("t1", LocalDate.of(2026, 1, 3), 4);

    assertThat(result)
        .containsExactly(
            LocalDate.of(2026, 1, 3),
            LocalDate.of(2026, 1, 2),
            LocalDate.of(2025, 12, 31),
            LocalDate.of(2025, 12, 30));
  }

  @Test
  void queryMustRemainTenantScoped() {
    List<LocalDate> t1 = repository.listRecentWorkdays("t1", LocalDate.of(2026, 1, 3), 10);

    assertThat(t1)
        .as("tenant t1 must only see workdays from its own bound calendar")
        .containsExactly(
            LocalDate.of(2026, 1, 3),
            LocalDate.of(2026, 1, 2),
            LocalDate.of(2025, 12, 31),
            LocalDate.of(2025, 12, 30));

    List<LocalDate> t2 = repository.listRecentWorkdays("t2", LocalDate.of(2026, 1, 3), 10);

    assertThat(t2)
        .as("tenant t2 should only see its own calendar day")
        .containsExactly(LocalDate.of(2026, 1, 3));
  }

  @Test
  void anotherTenantCalendarCannotBeBound() {
    assertThatThrownBy(() -> repository.setDefaultCalendar("t1", "cal-other", "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("calendar not found");
  }

  private static void insertCalendar(String id, String tenantId, int year) {
    jdbc.getJdbcTemplate()
        .update(
            """
            insert into platform_calendar(
              id, tenant_id, calendar_code, calendar_name,
              region_code, timezone, year,
              enabled, source_type, created_by
            )
            values (?, ?, ?, ?, 'CN',
                    'Asia/Shanghai', ?,
                    true, 'manual', 'system')
            """,
            id,
            tenantId,
            id,
            id,
            year);
  }

  private static void bind(String tenantId, int year, String calendarId) {
    jdbc.getJdbcTemplate()
        .update(
            """
            insert into platform_calendar_binding(
              tenant_id, calendar_year, calendar_id,
              created_by, updated_by
            )
            values (?, ?, ?, 'system', 'system')
            """,
            tenantId,
            year,
            calendarId);
  }

  private static void insertDay(
      String tenantId, String calendarId, LocalDate date, String type, boolean workday) {
    jdbc.getJdbcTemplate()
        .update(
            """
            insert into platform_calendar_day(
              id, tenant_id, calendar_id, calendar_date,
              day_of_week, day_type, is_workday,
              source_type, created_by
            )
            values (?, ?, ?, ?, ?, ?, ?,
                    'manual', 'system')
            """,
            calendarId + "-" + date,
            tenantId,
            calendarId,
            date,
            date.getDayOfWeek().getValue(),
            type,
            workday);
  }
}
