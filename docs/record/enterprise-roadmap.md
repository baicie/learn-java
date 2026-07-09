---
title: 工作记录企业级重做路线图
type: research
status: draft
phase: work-record
owner: platform-team
created: 2026-07-09
updated: 2026-07-09
related:
  - docs/record/api-contract.md
  - docs/record/schema-contract.md
  - docs/record/permission-contract.md
  - docs/record/acceptance-checklist.md
---

# 工作记录企业级重做路线图

## 1. 当前状态冻结

`feat/record-doc-portal` 当前实现进入冻结状态。

冻结含义：

1. 当前代码保留作为参考。
2. 不在当前不可用实现上继续堆业务功能。
3. 后续重做以本文档和配套 contract 文档为准。
4. 新增功能必须先满足契约，再进入实现。

当前已知问题：

1. record 模块页面和后端主链路基本不可用。
2. schema 扩展协议前后端不统一。
3. 导出动态列不完整。
4. 动态 JSONB 查询安全性不足。
5. 模板、字段、记录、列表、导出之间缺少企业级闭环。
6. web/portal 与 web/console 边界仍需在 CI 层收敛。

## 2. 前端边界

唯一主前端：`web/portal`

冻结规则：

1. 工作记录所有新增页面只允许进入 web/portal。
2. web/console 不再新增工作记录相关功能。
3. web/console 只作为历史回退和参考实现保留。
4. CI 默认只检查 web/portal。
5. 如需检查 web/console，使用 `RUN_LEGACY_CONSOLE_CI=1` 显式开启。

## 3. 后端边界

主应用：`apps/aiops-server`

模块边界：

- `modules/aiops-platform` - dictionary, calendar
- `modules/aiops-work-record` - template, schema, field index, record, query, export, audit

禁止：

1. work-record 直接依赖 alert / incident / inspection repository。
2. dictionary 反向依赖 work-record。
3. calendar 反向依赖 work-record。
4. api 层直接访问 JDBC。
5. domain 层依赖 Spring Web。

## 4. 第一版企业级范围

第一版做：

1. 用户管理复用 / 加固
2. 角色权限复用 / 加固
3. 平台字典
4. 平台工作日历基础 API
5. 模板管理
6. portal 原生字段设计器
7. Formily runtime
8. 记录填写 / 编辑 / 详情
9. 记录列表
10. 动态筛选
11. CSV 导出
12. 权限
13. 审计
14. 测试

第一版不做：

1. 不新增微前端
2. 不新增微服务
3. 不新增完整工单流
4. 不新增审批流
5. 不新增 SLA
6. 不新增 Excel 导入
7. 不新增附件
8. 不新增评论
9. 不新增 AI 总结
10. 不新增字段级权限
11. 不新增复杂流程
12. 不新增页面级低代码
13. 不新增业务 API
14. 不新增数据库 migration

## 5. 推荐执行顺序

- **Phase 0**: 冻结现状，重新定边界
- **Phase 1**: 工程基线重建
- **Phase 2**: 平台基础能力完善
- **Phase 3**: 数据库模型重做
- **Phase 4**: 后端领域模型重做
- **Phase 5**: 模板与 Schema 契约重做
- **Phase 6**: 模板管理 API
- **Phase 7**: portal 表单设计器重做
- **Phase 8**: 记录运行态重做
- **Phase 9**: 动态字段后端校验重做
- **Phase 10**: 记录列表重做
- **Phase 11**: 动态查询安全重做
- **Phase 12**: 导出能力重做
- **Phase 13**: 权限体系企业级加固
- **Phase 14**: 审计与变更追踪
- **Phase 15**: 工作日历接入工作记录
- **Phase 16**: 前端体验完善
- **Phase 17**: 测试体系补齐
- **Phase 18**: 企业级验收场景
- **Phase 19**: 生产化加固
- **Phase 20**: 后续增强

## 6. Phase 0 验收

1. 本文档存在。
2. api-contract.md 存在。
3. schema-contract.md 存在。
4. permission-contract.md 存在。
5. acceptance-checklist.md 存在。
6. scripts/ci/check-record-phase0-contracts.ts 通过。
7. scripts/ci/docs.sh 调用 Phase 0 契约检查。
8. scripts/ci/frontend.sh 默认只跑 web/portal。
9. web/console 仅在 `RUN_LEGACY_CONSOLE_CI=1` 时运行。
