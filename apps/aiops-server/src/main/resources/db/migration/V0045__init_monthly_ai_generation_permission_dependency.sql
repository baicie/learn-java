update iam.permission_definition
set dependencies_json = '["work-record:read:all"]'::jsonb,
    updated_at = now()
where permission_code = 'work-record:ai:generate';
