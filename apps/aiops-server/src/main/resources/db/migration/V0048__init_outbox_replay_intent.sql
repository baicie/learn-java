alter table automation_outbox
    add column if not exists replay_requested boolean not null default false;
