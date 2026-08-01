alter table tenant_security_event
    drop constraint if exists ck_tenant_security_event_type;

alter table tenant_security_event
    add constraint ck_tenant_security_event_type
    check (
        event_type in (
            'tenant_missing',
            'internal_auth_failed',
            'internal_auth_forbidden',
            'internal_auth_unavailable',
            'diagnosis_grant_invalid',
            'rate_limited',
            'quota_exceeded',
            'cross_tenant_denied',
            'internal_auth_succeeded'
        )
    );
