-- Phase 2: incident aggregation center
-- Adds: aggregation_key, last_seen_at, alert_count to incident; supporting indexes; the
-- "one active incident per aggregation_key" constraint; and the "an alert is linked to
-- at most one incident" constraint.

alter table incident
  add column if not exists aggregation_key varchar(512);

alter table incident
  add column if not exists last_seen_at timestamptz;

alter table incident
  add column if not exists alert_count integer not null default 0;

create index if not exists idx_incident_tenant_status_started
  on incident(tenant_id, status, started_at desc);

create index if not exists idx_incident_tenant_aggregation_key
  on incident(tenant_id, aggregation_key);

create unique index if not exists uq_incident_active_aggregation_key
  on incident(tenant_id, aggregation_key)
  where aggregation_key is not null
    and status in ('open', 'investigating', 'mitigating');

create index if not exists idx_incident_event_incident
  on incident_event(incident_id);

create unique index if not exists uq_incident_event_incident_event
  on incident_event(incident_id, event_type, event_id);

create unique index if not exists uq_incident_event_alert_once
  on incident_event(event_type, event_id)
  where event_type = 'alert';

create index if not exists idx_incident_timeline_incident_time
  on incident_timeline(incident_id, event_time desc);

create index if not exists idx_alert_event_tenant_status_fingerprint
  on alert_event(tenant_id, status, fingerprint, starts_at desc);
