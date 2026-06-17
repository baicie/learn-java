-- Phase 4.4 follow-up: enforce one agent_run per ai_diagnosis.
-- The Phase4.4 design says each ai_diagnosis row produced by the Python agent
-- can attach one agent_run. The initial V8 migration did not enforce this
-- relationship, so a transient retry or duplicate write could produce multiple
-- agent_run rows for the same diagnosis. This migration adds a unique index
-- on agent_run.diagnosis_id to guarantee the 1:1 relationship.
--
-- A separate agent_run_id still exists for the runId supplied by the Python
-- agent; this constraint only protects the Java-side relationship.

create unique index if not exists uq_agent_run_diagnosis
  on agent_run(diagnosis_id);
