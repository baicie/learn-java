---
title: Phase4.7 Generated Repository Migration
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---
# Phase4.7 Generated Repository Migration

## 背景

Phase4.6 已经接入 jOOQ codegen，并将 aiops-evidence 迁移到 generated Tables。

Phase4.7 继续迁移：

- aiops-ai-client
- aiops-rca

## 目标

- JdbcAiRepository 使用 io.aegisops.persistence.jooq.Tables
- JdbcRcaRepository 使用 io.aegisops.persistence.jooq.Tables
- AegisTables 标记 Deprecated
- 不修改 Repository interface
- 不修改 Service / Controller
- 不修改 API contract
- 不修改 Python Agent

## 不做

- 不删除 AegisTables
- 不引入 POJO / DAO
- 不修改 Flyway schema
- 不做业务逻辑改造

## 后续

Phase4.8 可以删除 AegisTables，或者先增加 lint/checkstyle 规则禁止新代码导入 AegisTables。
