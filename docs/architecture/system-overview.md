---
title: System Overview
type: architecture
status: draft
phase: global
owner: ai
created: 2026-06-12
updated: 2026-06-12
related: []
---

# System Overview

## Purpose

This document describes the high-level architecture of AegisOps / FaultLens.

## Core Flow

```txt
Zabbix alert
  ↓
AlertEvent
  ↓
Incident
  ↓
RCA evidence
  ↓
AI diagnosis
  ↓
Runbook
  ↓
Approved automation
  ↓
Postmortem
```

## Applications

- aiops-server
- aiops-worker
- aiops-runner

## Data Stores

- PostgreSQL
- Redis
- VictoriaMetrics
- ClickHouse
- MinIO
