---
title: Phase 20 执行基线
type: phase
status: accepted
phase: work-record-20
owner: ai
created: 2026-07-14
updated: 2026-07-14
related:
  - docs/api/platform-iam.md
  - apps/aiops-server/src/main/resources/db/migration/V0029__init_harden_portal_iam.sql
---

# Phase 20 执行基线

## 范围

Phase 20 共 19 项能力：原 17 项后续增强，以及用户管理、权限管理。后两项复用
`aiops-platform` 与 `aiops-security` 的统一 IAM，不在工作记录模块创建第二套用户、角色、权限表。

## 已完成的 IAM 基线

- 用户查询、新增、编辑、启用/禁用、锁定/解锁、角色分配和管理员密码重置；
- 角色查询、新增、编辑、权限分配、数据范围和自定义角色软删除；
- 权限目录查询及 Portal 权限树；
- 七个 IAM 管理权限逐接口校验，禁止仅依赖前端隐藏按钮；
- 用户名按租户唯一，用户、角色、授权与数据范围按 `tenant_id` 隔离；
- 禁止管理员停用或锁定自己，禁止移除自己的 `system_admin`，禁止移除最后一个可用系统管理员；
- 密码使用统一编码器落库，重置后清空登录失败与锁定状态；
- 用户与角色变更写入审计日志，内置角色禁止删除和越权创建。

## 实施顺序与门禁

1. Phase 19 P0 阻断项验收；
2. 本文 IAM 基线与 `V0029` 在空库、历史库验证；
3. 20.0–20.1：Outbox、异步任务、对象存储；
4. 20.2–20.7：按拆分文档逐阶段实施和验收；
5. 20.8：Portal、权限初始化、E2E、性能和生产验收。

Phase 20 业务迁移从 `V0030` 开始，预留 `V0029` 给 IAM 加固。任何子阶段未通过租户隔离、权限拒绝、审计、重试幂等和回滚验证，不得进入下一阶段。

## 验收证据

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) \
  mvn -B -ntp -pl modules/aiops-platform,modules/aiops-security -am -DskipITs test

pnpm -C web/portal run typecheck
pnpm -C web/portal run test
```

PostgreSQL 迁移集成测试使用 Testcontainers；本地 Docker 不可用时测试必须标记跳过，并在交付说明中明确，不得伪报通过。
