alter table execution_run
  add column if not exists execution_grant text;

alter table execution_run
  add column if not exists execution_snapshot_sha256 varchar(64);

alter table execution_run
  add column if not exists execution_grant_expires_at timestamptz;
