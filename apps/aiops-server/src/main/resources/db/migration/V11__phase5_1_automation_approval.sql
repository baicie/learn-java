-- Phase 5.1: AutomationPlan approval workflow.
-- This phase still does not execute any action.

alter table automation_plan
  drop constraint if exists ck_automation_plan_status;

alter table automation_plan
  add constraint ck_automation_plan_status
    check (status in (
      'draft',
      'pending_approval',
      'approved',
      'rejected',
      'superseded',
      'cancelled'
    ));

create table if not exists approval_policy (
  id varchar(64) primary key,
  tenant_id varchar(64) references tenant(id) on delete cascade,
  risk_level varchar(32) not null,
  required_approvals int not null default 1,
  require_comment boolean not null default false,
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_approval_policy_risk_level
    check (risk_level in ('low', 'medium', 'high', 'critical')),
  constraint ck_approval_policy_required_approvals
    check (required_approvals >= 0 and required_approvals <= 5)
);

create unique index if not exists uq_approval_policy_global_risk
  on approval_policy(risk_level)
  where tenant_id is null;

create unique index if not exists uq_approval_policy_tenant_risk
  on approval_policy(tenant_id, risk_level)
  where tenant_id is not null;

create table if not exists automation_approval (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  incident_id varchar(64) not null references incident(id) on delete cascade,
  plan_id varchar(64) not null references automation_plan(id) on delete cascade,
  status varchar(32) not null default 'pending',
  risk_level varchar(32) not null,
  required_approvals int not null,
  approved_count int not null default 0,
  rejected_count int not null default 0,
  submitted_by varchar(64) not null,
  submitted_at timestamptz not null default now(),
  completed_at timestamptz,
  reason text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_automation_approval_status
    check (status in ('pending', 'approved', 'rejected', 'cancelled')),
  constraint ck_automation_approval_risk_level
    check (risk_level in ('low', 'medium', 'high', 'critical')),
  constraint ck_automation_approval_counts
    check (approved_count >= 0 and rejected_count >= 0 and required_approvals >= 0)
);

create index if not exists idx_automation_approval_tenant_plan_created
  on automation_approval(tenant_id, plan_id, created_at desc);

create index if not exists idx_automation_approval_tenant_status
  on automation_approval(tenant_id, status);

create unique index if not exists uq_automation_approval_pending_plan
  on automation_approval(plan_id)
  where status = 'pending';

create table if not exists approval_decision (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  approval_id varchar(64) not null references automation_approval(id) on delete cascade,
  plan_id varchar(64) not null references automation_plan(id) on delete cascade,
  reviewer varchar(64) not null,
  decision varchar(32) not null,
  comment text,
  decided_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  constraint ck_approval_decision_decision
    check (decision in ('approve', 'reject'))
);

create unique index if not exists uq_approval_decision_reviewer_once
  on approval_decision(approval_id, reviewer);

create index if not exists idx_approval_decision_approval
  on approval_decision(approval_id, decided_at asc);

insert into approval_policy (
  id,
  tenant_id,
  risk_level,
  required_approvals,
  require_comment,
  enabled
)
select 'ap_global_low', null, 'low', 0, false, true
where not exists (
  select 1 from approval_policy where tenant_id is null and risk_level = 'low'
);

insert into approval_policy (
  id,
  tenant_id,
  risk_level,
  required_approvals,
  require_comment,
  enabled
)
select 'ap_global_medium', null, 'medium', 1, false, true
where not exists (
  select 1 from approval_policy where tenant_id is null and risk_level = 'medium'
);

insert into approval_policy (
  id,
  tenant_id,
  risk_level,
  required_approvals,
  require_comment,
  enabled
)
select 'ap_global_high', null, 'high', 1, true, true
where not exists (
  select 1 from approval_policy where tenant_id is null and risk_level = 'high'
);

insert into approval_policy (
  id,
  tenant_id,
  risk_level,
  required_approvals,
  require_comment,
  enabled
)
select 'ap_global_critical', null, 'critical', 2, true, true
where not exists (
  select 1 from approval_policy where tenant_id is null and risk_level = 'critical'
);
