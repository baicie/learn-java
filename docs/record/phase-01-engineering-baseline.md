---
title: Phase 1 工程基线重建
type: phase
status: draft
phase: work-record
owner: platform-team
created: 2026-07-09
updated: 2026-07-14
related:
  - docs/record/enterprise-roadmap.md
  - docs/record/api-contract.md
  - docs/record/schema-contract.md
  - docs/record/permission-contract.md
  - docs/record/acceptance-checklist.md
---

# Phase 1 工程基线重建

> 历史说明：本文记录最初的冻结基线。Portal 工作记录模块已在 Phase 21 解冻并迁移到路由导向目录；当前约束以 [ADR 0007](../adr/0007-portal-route-oriented-source-layout.md) 和 Phase 21 设计文档为准。

## 1. 目标

先让项目结构、构建、测试、CI 稳定。

## 2. 范围

本阶段做：

1. 确认 root package scripts 全部指向 web/portal。
2. 增加 aiops-server with-portal 打包 profile。
3. 保留 with-console 作为回退 profile。
4. 清理 web/portal 内不可用 record 页面，统一改成冻结占位。
5. 保留 aiops-work-record 模块。
6. 增加后端模块边界测试。
7. 增加 portal 冻结占位测试。
8. CI 加入 portal build / lint / test。
9. CI 加入 aiops-work-record test。
10. CI 加入 aiops-platform test。

## 3. 非目标

1. 不修复现有 record 业务逻辑。
2. 不新增业务 API。
3. 不新增数据库 migration。
4. 不做模板版本。
5. 不做列表、导出、设计器重写。

## 4. 验收命令

```bash
mvn -pl modules/aiops-work-record -am test
mvn -pl modules/aiops-platform -am test
mvn -pl apps/aiops-server -am test
pnpm -C web/portal run build
pnpm -C web/portal run lint
pnpm -C web/portal run test
pnpm exec tsx scripts/ci/check-record-phase1-baseline.ts
```

## 5. CI 验收

```bash
pnpm run ci:backend
pnpm run ci:frontend
pnpm run ci:docs
```

## 6. 通过标准

1. web/portal 是默认前端。
2. web/console 只在 RUN_LEGACY_CONSOLE_CI=1 时参与前端 CI。
3. aiops-server 同时保留 with-portal 和 with-console。
4. work-record 页面不再调用不可用业务实现。
5. work-record 和 platform 模块边界测试通过。
