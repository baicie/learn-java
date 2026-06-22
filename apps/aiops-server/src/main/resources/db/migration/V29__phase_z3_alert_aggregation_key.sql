-- Phase Z3: alert standardization and incident aggregation key.
-- fingerprint keeps per-alert identity.
-- aggregation_key groups multiple related alerts into one incident.

alter table alert_event
  add column if not exists aggregation_key varchar(512);

create index if not exists idx_alert_event_tenant_aggregation_key
  on alert_event(tenant_id, aggregation_key);

create index if not exists idx_alert_event_tenant_status_aggregation_key
  on alert_event(tenant_id, status, aggregation_key);

create index if not exists idx_incident_tenant_aggregation_key_status
  on incident(tenant_id, aggregation_key, status);