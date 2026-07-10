package io.aegisops.platform.calendar;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DefaultCalendarRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public DefaultCalendarRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<CalendarRecord> findDefaultCalendar(String tenantId, int year) {
    List<CalendarRecord> rows =
        jdbc.query(
            """
            select
                c.id,
                c.tenant_id,
                c.calendar_code,
                c.calendar_name,
                c.region_code,
                c.timezone,
                c.year,
                c.enabled,
                c.source_type,
                c.description,
                c.created_by,
                c.created_at,
                c.updated_at
            from platform_calendar_binding b
            join platform_calendar c
              on c.id = b.calendar_id
             and c.tenant_id = b.tenant_id
             and c.year = b.calendar_year
            where b.tenant_id = :tenantId
              and b.calendar_year = :year
              and c.enabled = true
            """,
            Map.of(
                "tenantId", tenantId,
                "year", year),
            this::mapCalendar);

    return rows.stream().findFirst();
  }

  public CalendarRecord setDefaultCalendar(String tenantId, String calendarId, String actorId) {
    CalendarRecord calendar =
        findCalendar(tenantId, calendarId)
            .orElseThrow(() -> new IllegalArgumentException("calendar not found"));

    if (!calendar.enabled()) {
      throw new IllegalStateException("disabled calendar cannot be default");
    }

    int changed =
        jdbc.update(
            """
            insert into platform_calendar_binding (
                tenant_id,
                calendar_year,
                calendar_id,
                created_by,
                updated_by
            )
            values (
                :tenantId,
                :year,
                :calendarId,
                :actorId,
                :actorId
            )
            on conflict (
                tenant_id,
                calendar_year
            )
            do update set
                calendar_id =
                    excluded.calendar_id,
                updated_by =
                    excluded.updated_by,
                updated_at =
                    now()
            """,
            Map.of(
                "tenantId", tenantId,
                "year", calendar.year(),
                "calendarId", calendar.id(),
                "actorId", actorOrSystem(actorId)));

    if (changed != 1) {
      throw new IllegalStateException("failed to bind default calendar");
    }

    return calendar;
  }

  public List<LocalDate> listRecentWorkdays(String tenantId, LocalDate anchorDate, int limit) {
    return jdbc.query(
        """
        select d.calendar_date
        from platform_calendar_day d
        join platform_calendar_binding b
          on b.tenant_id = d.tenant_id
         and b.calendar_id = d.calendar_id
         and b.calendar_year =
             extract(year from d.calendar_date)::integer
        join platform_calendar c
          on c.id = b.calendar_id
         and c.tenant_id = b.tenant_id
         and c.year = b.calendar_year
        where d.tenant_id = :tenantId
          and c.enabled = true
          and d.is_workday = true
          and d.calendar_date <= :anchorDate
        order by d.calendar_date desc
        limit :limit
        """,
        Map.of(
            "tenantId", tenantId,
            "anchorDate", anchorDate,
            "limit", limit),
        (rs, rowNumber) -> rs.getObject("calendar_date", LocalDate.class));
  }

  public List<CalendarDayRecord> listDefaultDays(String tenantId, LocalDate start, LocalDate end) {
    return jdbc.query(
        """
        select
            d.id,
            d.tenant_id,
            d.calendar_id,
            d.calendar_date,
            d.day_of_week,
            d.day_type,
            d.is_workday,
            d.holiday_code,
            d.holiday_name,
            d.source_type,
            d.remark,
            d.created_by,
            d.created_at,
            d.updated_at
        from platform_calendar_day d
        join platform_calendar_binding b
          on b.tenant_id = d.tenant_id
         and b.calendar_id = d.calendar_id
         and b.calendar_year =
             extract(year from d.calendar_date)::integer
        join platform_calendar c
          on c.id = b.calendar_id
         and c.tenant_id = b.tenant_id
         and c.year = b.calendar_year
        where d.tenant_id = :tenantId
          and c.enabled = true
          and d.calendar_date >= :start
          and d.calendar_date <= :end
        order by d.calendar_date asc
        """,
        Map.of(
            "tenantId", tenantId,
            "start", start,
            "end", end),
        this::mapDay);
  }

  private Optional<CalendarRecord> findCalendar(String tenantId, String calendarId) {
    List<CalendarRecord> rows =
        jdbc.query(
            """
            select
                id,
                tenant_id,
                calendar_code,
                calendar_name,
                region_code,
                timezone,
                year,
                enabled,
                source_type,
                description,
                created_by,
                created_at,
                updated_at
            from platform_calendar
            where tenant_id = :tenantId
              and id = :calendarId
            """,
            Map.of(
                "tenantId", tenantId,
                "calendarId", calendarId),
            this::mapCalendar);

    return rows.stream().findFirst();
  }

  private CalendarRecord mapCalendar(ResultSet rs, int rowNumber) throws SQLException {
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

  private CalendarDayRecord mapDay(ResultSet rs, int rowNumber) throws SQLException {
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

  private String actorOrSystem(String actorId) {
    return actorId == null || actorId.isBlank() ? "system" : actorId;
  }
}
