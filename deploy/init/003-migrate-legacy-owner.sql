\set ON_ERROR_STOP on

set role aegisops_admin;

select format('alter database %I owner to aegisops_app', :'database_name')
\gexec

select format('alter schema %I owner to aegisops_app', namespace.nspname)
from pg_namespace namespace
join pg_roles owner_role on owner_role.oid = namespace.nspowner
where owner_role.rolname = :'legacy_owner'
  and namespace.nspname <> 'information_schema'
  and namespace.nspname !~ '^pg_'
\gexec

select format(
  'alter %s %I.%I owner to aegisops_app',
  case relation.relkind
    when 'S' then 'sequence'
    when 'v' then 'view'
    when 'm' then 'materialized view'
    when 'f' then 'foreign table'
    when 'c' then 'type'
    else 'table'
  end,
  namespace.nspname,
  relation.relname
)
from pg_class relation
join pg_namespace namespace on namespace.oid = relation.relnamespace
join pg_roles owner_role on owner_role.oid = relation.relowner
where owner_role.rolname = :'legacy_owner'
  and namespace.nspname <> 'information_schema'
  and namespace.nspname !~ '^pg_'
  and relation.relkind in ('r', 'p', 'S', 'v', 'm', 'f', 'c')
  and not exists (
    select 1
    from pg_depend dependency
    where dependency.classid = 'pg_class'::regclass
      and dependency.objid = relation.oid
      and dependency.deptype = 'e'
  )
order by case relation.relkind when 'S' then 2 else 1 end
\gexec

select format(
  'alter %s %I.%I(%s) owner to aegisops_app',
  case routine.prokind when 'p' then 'procedure' else 'function' end,
  namespace.nspname,
  routine.proname,
  pg_get_function_identity_arguments(routine.oid)
)
from pg_proc routine
join pg_namespace namespace on namespace.oid = routine.pronamespace
join pg_roles owner_role on owner_role.oid = routine.proowner
where owner_role.rolname = :'legacy_owner'
  and namespace.nspname <> 'information_schema'
  and namespace.nspname !~ '^pg_'
  and routine.prokind in ('f', 'p', 'w')
  and not exists (
    select 1
    from pg_depend dependency
    where dependency.classid = 'pg_proc'::regclass
      and dependency.objid = routine.oid
      and dependency.deptype = 'e'
  )
\gexec

select format(
  'alter %s %I.%I owner to aegisops_app',
  case type_definition.typtype when 'd' then 'domain' else 'type' end,
  namespace.nspname,
  type_definition.typname
)
from pg_type type_definition
join pg_namespace namespace on namespace.oid = type_definition.typnamespace
join pg_roles owner_role on owner_role.oid = type_definition.typowner
where owner_role.rolname = :'legacy_owner'
  and namespace.nspname <> 'information_schema'
  and namespace.nspname !~ '^pg_'
  and type_definition.typtype in ('d', 'e', 'r', 'm')
  and not exists (
    select 1
    from pg_depend dependency
    where dependency.classid = 'pg_type'::regclass
      and dependency.objid = type_definition.oid
      and dependency.deptype = 'e'
  )
\gexec
