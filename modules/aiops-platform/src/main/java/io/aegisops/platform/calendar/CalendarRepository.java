package io.aegisops.platform.calendar;

import io.aegisops.common.id.Ids;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CalendarRepository {
  private final JdbcTemplate jdbc;

  public CalendarRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<CalendarRecord> listCalendars(String tenantId) {
    return jdbc.query(
        """
            select id, tenant_id, calendar_code, calendar_name, region_code, timezone,
                   year, enabled, source_type, description, created_by, created_at, updated_at
              from platform_calendar
             where tenant_id = ?
             order by year desc, calendar_code asc
            """,
        (rs, rowNum) -> mapCalendar(rs),
        tenantId);
  }

  public Optional<CalendarRecord> findCalendar(String tenantId, String calendarId) {
    List<CalendarRecord> rows =
        jdbc.query(
            """
                select id, tenant_id, calendar_code, calendar_name, region_code, timezone,
                       year, enabled, source_type, description, created_by, created_at, updated_at
                  from platform_calendar
                 where tenant_id = ? and id = ?
                """,
            (rs, rowNum) -> mapCalendar(rs),
            tenantId,
            calendarId);
    return rows.stream().findFirst();
  }

  public List<CalendarDayRecord> listDays(
      String tenantId, String calendarId, LocalDate start, LocalDate end) {
    return jdbc.query(
        """
            select id, tenant_id, calendar_id, calendar_date, day_of_week, day_type,
                   is_workday, holiday_code, holiday_name, source_type, remark,
                   created_by, created_at, updated_at
              from platform_calendar_day
             where tenant_id = ?
               and calendar_id = ?
               and calendar_date >= ?
               and calendar_date <= ?
             order by calendar_date asc
            """,
        (rs, rowNum) -> mapDay(rs),
        tenantId,
        calendarId,
        start,
        end);
  }

  public Optional<CalendarDayRecord> findDay(String tenantId, String calendarId, LocalDate date) {
    List<CalendarDayRecord> rows =
        jdbc.query(
            """
                select id, tenant_id, calendar_id, calendar_date, day_of_week, day_type,
                       is_workday, holiday_code, holiday_name, source_type, remark,
                       created_by, created_at, updated_at
                  from platform_calendar_day
                 where tenant_id = ?
                   and calendar_id = ?
                   and calendar_date = ?
                """,
            (rs, rowNum) -> mapDay(rs),
            tenantId,
            calendarId,
            date);
    return rows.stream().findFirst();
  }

  public CalendarDayRecord upsertDay(
      String tenantId, String calendarId, CalendarDayMutation mutation, String actor) {
    LocalDate date = mutation.date();
    String id = findDay(tenantId, calendarId, date).map(day -> day.id()).orElseGet(Ids::newId);

    jdbc.update(
        """
            insert into platform_calendar_day(
              id, tenant_id, calendar_id, calendar_date, day_of_week, day_type,
              is_workday, holiday_code, holiday_name, source_type, remark, created_by)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (tenant_id, calendar_id, calendar_date) do update
            set day_of_week = excluded.day_of_week,
                day_type = excluded.day_type,
                is_workday = excluded.is_workday,
                holiday_code = excluded.holiday_code,
                holiday_name = excluded.holiday_name,
                source_type = excluded.source_type,
                remark = excluded.remark,
                updated_at = now()
            """,
        id,
        tenantId,
        calendarId,
        date,
        date.getDayOfWeek().getValue(),
        mutation.dayType(),
        mutation.workday(),
        mutation.holidayCode(),
        mutation.holidayName(),
        defaultText(mutation.sourceType(), "manual"),
        mutation.remark(),
        actor);

    return findDay(tenantId, calendarId, date).orElseThrow();
  }

  public int countWorkdays(String tenantId, String calendarId, LocalDate start, LocalDate end) {
    Integer count =
        jdbc.queryForObject(
            """
                select count(*)
                  from platform_calendar_day
                 where tenant_id = ?
                   and calendar_id = ?
                   and calendar_date >= ?
                   and calendar_date <= ?
                   and is_workday = true
                """,
            Integer.class,
            tenantId,
            calendarId,
            start,
            end);
    return count == null ? 0 : count;
  }

  private CalendarRecord mapCalendar(ResultSet rs) throws SQLException {
    return new CalendarRecord(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("calendar_code"),
        rs.getString("calendar_name"),
        rs.getString("region_code"),
        rs.getString("timezone"),
        rs.getInt("year"),
        rs.getBoolean("enabled"),
        rs.getString("source_type"),
        rs.getString("description"),
        rs.getString("created_by"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class));
  }

  private CalendarDayRecord mapDay(ResultSet rs) throws SQLException {
    return new CalendarDayRecord(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("calendar_id"),
        rs.getObject("calendar_date", LocalDate.class),
        rs.getInt("day_of_week"),
        rs.getString("day_type"),
        rs.getBoolean("is_workday"),
        rs.getString("holiday_code"),
        rs.getString("holiday_name"),
        rs.getString("source_type"),
        rs.getString("remark"),
        rs.getString("created_by"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class));
  }

  private String defaultText(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
