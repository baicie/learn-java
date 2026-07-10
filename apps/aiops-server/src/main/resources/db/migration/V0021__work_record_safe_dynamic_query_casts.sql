-- Phase 11: Safe JSONB cast functions for dynamic query resilience
-- Tolerates legacy dirty data (e.g. "unknown" for number, non-ISO date strings)
-- without causing the entire query to fail with a cast error.

create or replace function work_record.try_numeric(p_value text)
returns numeric
language plpgsql
immutable
strict
parallel safe
as $$
begin
  return p_value::numeric;
exception
  when others then
    return null;
end;
$$;

create or replace function work_record.try_date(p_value text)
returns date
language plpgsql
immutable
strict
parallel safe
as $$
begin
  return p_value::date;
exception
  when others then
    return null;
end;
$$;

create or replace function work_record.try_timestamptz(p_value text)
returns timestamptz
language plpgsql
immutable
strict
parallel safe
as $$
begin
  return p_value::timestamptz;
exception
  when others then
    return null;
end;
$$;

comment on function work_record.try_numeric(text)
is 'Safely parses a JSONB text value as numeric; returns null for invalid legacy values';
comment on function work_record.try_date(text)
is 'Safely parses a JSONB text value as date; returns null for invalid legacy values';
comment on function work_record.try_timestamptz(text)
is 'Safely parses a JSONB text value as timestamptz; returns null for invalid legacy values';
