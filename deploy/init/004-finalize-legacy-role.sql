\set ON_ERROR_STOP on

set role aegisops_admin;

select format('revoke %I from %I', granted_role.rolname, :'legacy_owner')
from pg_auth_members membership
join pg_roles granted_role on granted_role.oid = membership.roleid
join pg_roles member_role on member_role.oid = membership.member
where member_role.rolname = :'legacy_owner'
\gexec

select format('revoke %I from %I', :'legacy_owner', member_role.rolname)
from pg_auth_members membership
join pg_roles granted_role on granted_role.oid = membership.roleid
join pg_roles member_role on member_role.oid = membership.member
where granted_role.rolname = :'legacy_owner'
\gexec

select format(
  'alter role %I with nologin nocreatedb nocreaterole noinherit noreplication password null',
  :'legacy_owner'
)
where :'legacy_owner' = 'aegisops'
  and exists (select 1 from pg_roles where rolname = :'legacy_owner')
\gexec

select format(
  'alter role %I with nosuperuser',
  :'legacy_owner'
)
from pg_roles legacy_role
where legacy_role.rolname = :'legacy_owner'
  and legacy_role.oid <> 10
\gexec

select format(
  'alter role %I rename to aegisops_bootstrap_disabled',
  :'legacy_owner'
)
from pg_roles legacy_role
where legacy_role.rolname = :'legacy_owner'
  and legacy_role.oid = 10
  and not exists (
    select 1 from pg_roles where rolname = 'aegisops_bootstrap_disabled'
  )
\gexec

select 'create role aegisops with nologin nosuperuser nocreatedb nocreaterole noinherit noreplication password null'
where not exists (select 1 from pg_roles where rolname = 'aegisops')
\gexec
