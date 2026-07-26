-- Provide every tenant a complete baseline calendar. Holiday imports only override exceptions.
-- Version 0045 is already allocated to the monthly AI generation permission dependency.
-- Enforce the new range for all new writes without breaking upgrades that still have legacy
-- 2051-2100 bindings. Clean databases validate the constraint immediately.
do $calendar_range$
begin
    alter table platform_calendar_binding
    drop constraint if exists ck_platform_calendar_binding_year;

    alter table platform_calendar_binding
    add constraint ck_platform_calendar_binding_year
    check (calendar_year between 2000 and 2050)
    not valid;

    if not exists (
        select 1
        from platform_calendar_binding
        where calendar_year not between 2000 and 2050
    ) then
        alter table platform_calendar_binding
        validate constraint ck_platform_calendar_binding_year;
    end if;
end;
$calendar_range$;

create or replace function public.ensure_platform_work_calendars(p_tenant_id varchar)
returns void
language plpgsql
as $$
begin
    insert into platform_calendar (
        id, tenant_id, calendar_code, calendar_name, region_code, timezone,
        year, enabled, source_type, created_by
    )
    select
        concat('calendar-', md5(p_tenant_id || ':' || year_value::text)),
        p_tenant_id,
        concat('CN_', year_value),
        concat('China Mainland ', year_value, ' Work Calendar'),
        'CN',
        'Asia/Shanghai',
        year_value,
        true,
        'generated',
        'system'
    from generate_series(2000, 2050) as years(year_value)
    on conflict (tenant_id, calendar_code) do update
    set enabled = true,
        updated_at = now()
    where platform_calendar.year = excluded.year;

    insert into platform_calendar_day (
        id, tenant_id, calendar_id, calendar_date, day_of_week, day_type,
        is_workday, source_type, created_by
    )
    select
        concat(
            'calendar-day-',
            md5(calendar.tenant_id || ':' || calendar.id || ':' || day_value::date::text)
        ),
        calendar.tenant_id,
        calendar.id,
        day_value::date,
        extract(isodow from day_value)::integer,
        case when extract(isodow from day_value) in (6, 7) then 'WEEKEND' else 'WORKDAY' end,
        extract(isodow from day_value) not in (6, 7),
        'generated',
        'system'
    from platform_calendar calendar
    cross join lateral generate_series(
        make_date(calendar.year, 1, 1), make_date(calendar.year, 12, 31), interval '1 day'
    ) as dates(day_value)
    where calendar.tenant_id = p_tenant_id
      and calendar.year between 2000 and 2050
    on conflict (tenant_id, calendar_id, calendar_date) do nothing;

    with ranked as (
        select
            tenant_id,
            year,
            id,
            row_number() over (
                partition by tenant_id, year
                order by
                    (calendar_code = concat('CN_', year)) desc,
                    updated_at desc,
                    created_at desc,
                    id asc
            ) as rank
        from platform_calendar
        where tenant_id = p_tenant_id
          and year between 2000 and 2050
          and enabled = true
    )
    insert into platform_calendar_binding (
        tenant_id, calendar_year, calendar_id, created_by, updated_by
    )
    select tenant_id, year, id, 'system', 'system'
    from ranked
    where rank = 1
    on conflict (tenant_id, calendar_year) do nothing;
end;
$$;

update platform_permission_code
set permission_name = case permission_code
        when 'platform:calendar:import' then '导入法定节假日'
        else permission_name
    end,
    description = case permission_code
        when 'platform:calendar:write' then '覆盖单日设置和调整默认日历'
        when 'platform:calendar:import' then '下载 XLSX 模板并导入法定节假日'
    end,
    updated_at = now()
where permission_code in (
    'platform:calendar:write',
    'platform:calendar:import'
);

update iam.permission
set permission_name = case permission_code
        when 'platform:calendar:import' then '导入法定节假日'
        else permission_name
    end,
    description = case permission_code
        when 'platform:calendar:write' then '覆盖单日设置和调整默认日历'
        when 'platform:calendar:import' then '下载 XLSX 模板并导入法定节假日'
    end,
    updated_at = now()
where permission_code in (
    'platform:calendar:write',
    'platform:calendar:import'
);

do $calendar_backfill$
declare
    tenant_row record;
begin
    for tenant_row in select id from tenant loop
        perform public.ensure_platform_work_calendars(tenant_row.id);
    end loop;
end;
$calendar_backfill$;

create or replace function public.initialize_platform_work_calendars()
returns trigger
language plpgsql
as $$
begin
    perform public.ensure_platform_work_calendars(new.id);
    return new;
end;
$$;

drop trigger if exists trg_initialize_platform_work_calendars on tenant;
create trigger trg_initialize_platform_work_calendars
after insert on tenant
for each row
execute function public.initialize_platform_work_calendars();
