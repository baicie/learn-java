\set ON_ERROR_STOP on

select format(
  'create role aegisops_admin login superuser createdb createrole inherit noreplication password %L',
  :'admin_password'
)
where not exists (select 1 from pg_roles where rolname = 'aegisops_admin')
\gexec

select format(
  'alter role aegisops_admin with login superuser createdb createrole inherit noreplication password %L',
  :'admin_password'
)
\gexec

select format(
  'create role aegisops_app login nosuperuser nocreatedb nocreaterole noinherit noreplication password %L',
  :'app_password'
)
where not exists (select 1 from pg_roles where rolname = 'aegisops_app')
\gexec

select format(
  'alter role aegisops_app with login nosuperuser nocreatedb nocreaterole noinherit noreplication password %L',
  :'app_password'
)
\gexec

select format(
  'create role aegisops_runner login nosuperuser nocreatedb nocreaterole noinherit noreplication password %L',
  :'runner_password'
)
where not exists (select 1 from pg_roles where rolname = 'aegisops_runner')
\gexec

select format(
  'alter role aegisops_runner with login nosuperuser nocreatedb nocreaterole noinherit noreplication password %L',
  :'runner_password'
)
\gexec

select format('revoke %I from aegisops_runner', granted_role.rolname)
from pg_auth_members membership
join pg_roles granted_role on granted_role.oid = membership.roleid
join pg_roles member_role on member_role.oid = membership.member
where member_role.rolname = 'aegisops_runner'
\gexec

revoke create on schema public from public;
grant usage, create on schema public to aegisops_app;
grant usage on schema public to aegisops_runner;
revoke temporary on database :"database_name" from public;
grant connect, create, temporary on database :"database_name" to aegisops_app;
revoke all on database :"database_name" from aegisops_runner;
grant connect on database :"database_name" to aegisops_runner;
