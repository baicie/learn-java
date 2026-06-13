---
title: Phase 1 Zabbix Sync
type: phase
status: accepted
phase: phase-1
owner: ai
created: 2026-06-14
updated: 2026-06-14
related:
  - docs/designs/phase-1/2026-06-14-phase-1-design.md
---

# Phase 1 — Zabbix Datasource Sync

## Goal

把 Zabbix 接入 AegisOps 作为第一个外部数据源，把 Zabbix Host / Problem 同步为 `Asset` / `AlertEvent`，并提供可追踪的同步执行记录。

## Deliverables

- `modules/aiops-zabbix-adapter` 新模块
- `aiops-datasource` 新增 create / test / sync / sync-runs API
- 迁移 `V2__phase1_zabbix_sync.sql`
- 最小前端操作页面（创建数据源、Test、Sync、查看资产/告警）
- 设计文档 `docs/designs/phase-1/2026-06-14-phase-1-design.md`

## Acceptance

- 可以登录控制台
- 可以创建 Zabbix 数据源
- 点击 Test 返回 Zabbix 版本
- 点击 Sync 同步 host 与 problem
- `asset` 表出现 `source = zabbix` 的 host
- Zabbix 有 active problem 时 `alert_event` 表出现 `source = zabbix` 的告警
- `datasource_sync_run` 记录每次同步状态与统计
- Dashboard 的 Assets / Alerts 数量会随同步变化
