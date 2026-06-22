-- Phase Z2 compatibility: alert_event recovery timestamp.
-- ZabbixWebhookService writes ends_at for RESOLVED events.
-- V1 already declares ends_at on alert_event; this migration is an
-- explicit idempotent guard for environments that may have applied
-- V1 before the column was added, and adds a supporting index for
-- tenant-scoped recovery-time queries.

alter table alert_event
  add column if not exists ends_at timestamptz;

create index if not exists idx_alert_event_tenant_ends_at
  on alert_event(tenant_id, ends_at desc);
