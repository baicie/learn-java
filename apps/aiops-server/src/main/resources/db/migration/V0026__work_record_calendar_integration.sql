-- Phase 15: 工作日历接入工作记录。
-- 一个租户在一个自然年度只能绑定一个默认工作日历。

do $$
begin
    if not exists (
        select 1
          from pg_constraint
         where conname =
               'uq_platform_calendar_id_tenant_year'
    ) then
        alter table platform_calendar
            add constraint
            uq_platform_calendar_id_tenant_year
            unique (id, tenant_id, year);
    end if;
end
$$;

create table if not exists
platform_calendar_binding (
    tenant_id varchar(64) not null,
    calendar_year integer not null,
    calendar_id varchar(64) not null,

    created_by varchar(64) not null
        default 'system',
    updated_by varchar(64) not null
        default 'system',

    created_at timestamptz not null
        default now(),
    updated_at timestamptz not null
        default now(),

    primary key (
        tenant_id,
        calendar_year
    ),

    constraint
    ck_platform_calendar_binding_year
        check (
            calendar_year between 2000 and 2100
        ),

    constraint
    fk_platform_calendar_binding_calendar
        foreign key (
            calendar_id,
            tenant_id,
            calendar_year
        )
        references platform_calendar (
            id,
            tenant_id,
            year
        )
        on delete cascade
);

create index if not exists
idx_platform_calendar_binding_calendar
    on platform_calendar_binding (
        tenant_id,
        calendar_id
    );

-- 为已有租户的每个年度自动选择一个启用日历。
-- 优先选择最近更新的日历。
with ranked as (
    select
        c.tenant_id,
        c.year as calendar_year,
        c.id as calendar_id,
        row_number() over (
            partition by
                c.tenant_id,
                c.year
            order by
                c.updated_at desc,
                c.created_at desc,
                c.id asc
        ) as rn
    from platform_calendar c
    where c.enabled = true
)
insert into platform_calendar_binding (
    tenant_id,
    calendar_year,
    calendar_id,
    created_by,
    updated_by
)
select
    tenant_id,
    calendar_year,
    calendar_id,
    'system',
    'system'
from ranked
where rn = 1
on conflict (
    tenant_id,
    calendar_year
) do nothing;

-- 新年度首次创建日历时，自动成为该年度默认日历。
create or replace function
public.bind_first_platform_calendar()
returns trigger
language plpgsql
as $$
begin
    if new.enabled = true then
        insert into platform_calendar_binding (
            tenant_id,
            calendar_year,
            calendar_id,
            created_by,
            updated_by
        )
        values (
            new.tenant_id,
            new.year,
            new.id,
            coalesce(new.created_by, 'system'),
            coalesce(new.created_by, 'system')
        )
        on conflict (
            tenant_id,
            calendar_year
        ) do nothing;
    end if;

    return new;
end;
$$;

drop trigger if exists
trg_bind_first_platform_calendar
on platform_calendar;

create trigger
trg_bind_first_platform_calendar
after insert
on platform_calendar
for each row
execute function
public.bind_first_platform_calendar();
