-- Playwright E2E ephemeral users
\set ON_ERROR_STOP on

do $seed$
declare
    target_tenant_id varchar(64);
begin
    select id
      into strict target_tenant_id
      from tenant
     where code = 'default';

    insert into sys_user(
        id,
        tenant_id,
        username,
        display_name,
        email,
        password_hash,
        status
    )
    values
        (
            'phase18-e2e-user-a',
            target_tenant_id,
            'phase18-user-a',
            'Phase 18 用户 A',
            'phase18-user-a@example.test',
            '$2y$10$pFjCvkAh1bK2/rd6gPcoUumJv2TMi6zQsB0IhvlWViN96/TdaaAhW',
            'active'
        ),
        (
            'phase18-e2e-user-b',
            target_tenant_id,
            'phase18-user-b',
            'Phase 18 用户 B',
            'phase18-user-b@example.test',
            '$2y$10$pFjCvkAh1bK2/rd6gPcoUumJv2TMi6zQsB0IhvlWViN96/TdaaAhW',
            'active'
        )
    on conflict (id) do update
       set tenant_id = excluded.tenant_id,
           username = excluded.username,
           display_name = excluded.display_name,
           email = excluded.email,
           password_hash = excluded.password_hash,
           status = 'active',
           updated_at = now();

    insert into iam.user_role(
        tenant_id,
        user_id,
        role_code,
        created_by
    )
    values
        (
            target_tenant_id,
            'phase18-e2e-user-a',
            'normal_user',
            'phase18-e2e'
        ),
        (
            target_tenant_id,
            'phase18-e2e-user-b',
            'normal_user',
            'phase18-e2e'
        )
    on conflict do nothing;

    insert into iam.user_role(
        tenant_id,
        user_id,
        role_code,
        created_by
    )
    select
        tenant_id,
        id,
        'system_admin',
        'phase18-e2e'
      from sys_user
     where username = 'admin'
       and tenant_id = target_tenant_id
    on conflict do nothing;
end
$seed$;
