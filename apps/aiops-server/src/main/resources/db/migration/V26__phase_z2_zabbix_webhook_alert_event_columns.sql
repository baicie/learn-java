-- Phase Z2: Zabbix Webhook real-time ingestion.
-- Adds updated_at to alert_event for webhook-driven updates and
-- supporting indexes for the (tenant, source, status) and
-- (tenant, source, source_event_id) query paths used by the
-- webhook controller and the incident aggregation pipeline.

alter table alert_event
  add column if not exists updated_at timestamptz not null default now();

create index if not exists idx_alert_event_tenant_source_status
  on alert_event(tenant_id, source, status);

create index if not exists idx_alert_event_tenant_updated_at
  on alert_event(tenant_id, updated_at desc);
