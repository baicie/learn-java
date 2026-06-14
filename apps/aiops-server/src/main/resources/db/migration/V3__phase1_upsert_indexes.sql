-- Phase 1.0 hardening: add partial unique indexes so datasource sync can do
-- atomic ON CONFLICT upserts against (tenant_id, source, source_id) /
-- (tenant_id, source, source_event_id). Partial (WHERE ... IS NOT NULL)
-- preserves the original "manual / unsynced" rows that legitimately have a
-- null source id, so this is a backwards-compatible constraint.

create unique index if not exists uq_asset_tenant_source_source_id
  on asset (tenant_id, source, source_id)
  where source_id is not null;

create unique index if not exists uq_alert_event_tenant_source_source_event_id
  on alert_event (tenant_id, source, source_event_id)
  where source_event_id is not null;
