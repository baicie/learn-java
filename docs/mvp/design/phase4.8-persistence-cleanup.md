---
title: Phase4.8 Persistence Cleanup
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---
# Phase4.8 Persistence Cleanup

## 背景

Phase4.5 引入 no-codegen jOOQ。
Phase4.6 引入 jOOQ generated Tables 并迁移 aiops-evidence。
Phase4.7 迁移 aiops-ai-client 与 aiops-rca。

Phase4.8 收口持久层命名和过渡层。

## 目标

- 删除 AegisTables
- Repository 命名从 JdbcXxxRepository 收敛为 JooqXxxRepository
- 禁止 main source 继续导入 AegisTables
- 禁止核心 Repository 使用 Jdbc 前缀
- 保留 AegisJooq JSONB helper
- 不修改 API contract
- 不修改 Python Agent

## 删除

- io.aegisops.persistence.AegisTables
- AegisTablesTest
- JdbcAiRepository
- JdbcRcaRepository
- JdbcEvidenceRepository

## 新增

- JooqAiRepository
- JooqRcaRepository
- JooqEvidenceRepository
- Persistence architecture tests

## 验收

- main source 不存在 AegisTables import
- main source 不存在 Jdbc\*Repository.java
- apps/aiops-server -am test 通过
