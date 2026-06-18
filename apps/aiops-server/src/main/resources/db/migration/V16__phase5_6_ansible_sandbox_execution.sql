-- Phase 5.6: Ansible sandbox check execution.
-- This phase allows runner to execute ansible-playbook --check in an isolated workspace.
-- It still does not allow live ansible execution.

alter table ansible_execution_policy
  add column if not exists allow_check_execution boolean not null default true;

create index if not exists idx_ansible_policy_check_execution
  on ansible_execution_policy(tenant_id, allow_check_execution, enabled);
