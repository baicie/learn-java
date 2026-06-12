# Phase Checklist

Use this checklist when starting, reviewing, or closing a phase.

## Phase Start Checklist

Before beginning a new phase:

- [ ] Previous phase is accepted (all acceptance criteria met)
- [ ] Phase design document exists under `docs/phases/<phase>/`
- [ ] All designs for the phase are documented under `docs/designs/<phase>/`
- [ ] ADRs are created for any new architectural decisions
- [ ] Existing ADRs are not being violated
- [ ] Module boundaries defined in `docs/architecture/` are respected

## Phase Working Checklist

During a phase:

- [ ] Every feature has a design document before implementation
- [ ] Every database change has a Flyway migration
- [ ] External systems are accessed through adapters only
- [ ] Tenant isolation is preserved in every query
- [ ] Audit logs are created for sensitive operations
- [ ] No feature from a future phase is implemented
- [ ] No premature microservices extraction
- [ ] Tests are written for core domain logic
- [ ] Frontmatter is present and valid on every new document
- [ ] Documents are placed in the correct directory

## Phase Review Checklist

Before accepting a phase:

- [ ] All acceptance criteria are verified
- [ ] All planned deliverables are implemented
- [ ] Design documents are updated to reflect what was built
- [ ] OpenAPI spec reflects any API changes
- [ ] README / AGENTS.md / SKILL.md reflect any new conventions
- [ ] No known P0 or P1 issues remain open
- [ ] Code compiles and tests pass
- [ ] MVP demo path still works end-to-end

## Phase Close Checklist

When closing a phase:

- [ ] Phase acceptance document is updated with final status
- [ ] Phase status is set to `accepted`
- [ ] Next phase design document is created or started
- [ ] Any in-progress designs/reviews/fixes from this phase are either completed or explicitly deferred
- [ ] `docs/INDEX.md` is regenerated

## Per-Phase Gate

Each phase must pass the MVP demo gate before closing:

| Phase | Gate |
|-------|------|
| Phase 0 | Can log in, empty dashboard renders, API docs accessible |
| Phase 1 | Zabbix datasource syncs hosts and alerts, AlertEvent created |
| Phase 2 | Alerts aggregate into Incidents, Incident detail shows timeline |
| Phase 3 | Incident detail shows RCA evidence chain with metrics |
| Phase 4 | AI diagnosis produces structured output with evidence |
| Phase 5 | Approved Runbook executes via Ansible, logs stream back |
| Phase 6 | Closing Incident generates postmortem draft |
