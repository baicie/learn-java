---
title: Documentation Guide
type: operation
status: accepted
phase: global
owner: ai
created: 2026-06-12
updated: 2026-06-12
related:
  - docs/INDEX.md
---

# Documentation Guide

This directory contains all project documentation for AegisOps / FaultLens.

## Main Sections

- architecture: stable architecture documents
- adr: architecture decision records
- phases: phase plans, acceptance, and progress
- designs: feature designs by phase
- reviews: design and code reviews
- fixes: bugfix and repair plans
- api: API specifications and conventions
- database: schema and migration docs
- integrations: external system integration docs
- ai: AI, RCA, prompt, and tool-calling docs
- operations: local development, deployment, troubleshooting
- runbooks: built-in product runbooks
- research: product and technical research
- _templates: document templates

## Required Frontmatter

Every document must have YAML frontmatter:

```yaml
---
title: Document Title
type: design
status: draft
phase: phase-0
owner: ai
created: 2026-06-12
updated: 2026-06-12
related: []
---
```

## Commands

```bash
node scripts/docs.mjs init
node scripts/docs.mjs new design project-foundation --title "Project Foundation Design" --phase phase-0
node scripts/docs.mjs new adr use-java-spring-boot --title "Use Java Spring Boot"
node scripts/docs.mjs new fix zabbix-auth-failed --title "Fix Zabbix Auth Failed" --phase phase-1
node scripts/docs.mjs check
node scripts/docs.mjs index
```

## Allowed Document Types

- architecture
- adr
- phase
- design
- review
- fix
- api
- database
- integration
- ai
- operation
- runbook
- research

## Allowed Status Values

- draft
- review
- accepted
- deprecated

## Naming Conventions

- Use lowercase filenames with kebab-case
- Use date prefix for process documents: `YYYY-MM-DD-slug.md`
- Use numbered filenames for ADR: `NNNN-slug.md`
- Do not use spaces in filenames

## Important Rules

- Do not create documents outside `docs/`
- Do not edit `docs/INDEX.md` manually (it is generated)
- Accepted ADRs should not be rewritten — create a new ADR if the decision changes
- All phase-scoped documents (designs, reviews, fixes) must be placed under the correct phase directory
- Stable documents (architecture, api, database, integrations, ai, operations, runbooks, research) go at the top level
