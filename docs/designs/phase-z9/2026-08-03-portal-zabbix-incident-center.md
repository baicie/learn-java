---
title: Portal Zabbix Incident Center 可视化闭环
type: design
status: accepted
phase: phase-z9
owner: ai
created: 2026-08-03
updated: 2026-08-03
related: []
---

# Portal Zabbix Incident Center 可视化闭环

## 范围

本设计补齐当前 `web/portal` 对已有 AegisOps 后端运维数据的可视化入口，不新增数据库表、
不改变租户隔离和 Zabbix Adapter。交付目标是让当前 Zabbix MVP 验收数据从 API 进入 Portal：

```text
Zabbix -> AlertEvent -> Incident -> Evidence/RCA -> AI Diagnosis -> Report
```

## 现状问题

后端已提供 `/api/alerts`、`/api/incidents`、Incident detail/timeline、Evidence、RCA、AI
Diagnosis 和 Report 接口，但 Portal 只有数据源、资产和工作记录路由。首页只查询工作记录，
因此后端记录存在时用户仍无法在界面看到。

## 方案

1. 在 `src/api/operations` 增加带 Zod 校验的运维客户端，复用现有 Axios、JWT 和
   `ApiResponse` envelope；读取能力独立缓存，RCA、AI Diagnosis 与 Report 未生成时返回空态。
2. 新增 `/alerts` 列表和 `/incidents` 列表、`/incidents/$incidentId` 详情路由；分页与筛选
   通过 TanStack Router search 参数保存，服务端接口当前返回最近 100 条，前端负责可见列表筛选。
3. Incident 详情采用三栏信息架构：左侧时间线，中间 Incident/告警/RCA/AI 摘要，右侧 Evidence
   与 Report；移动端按纵向区块排列。
4. 导航项按 `alert:read`、`incident:read` 过滤；详情中的 Diagnose、Resolve、Close 等
   操作按 `incident:diagnose` / `incident:write` 显示，后端仍是最终权限边界。
5. 使用现有语义色、Badge、Table、Tabs、Skeleton、Empty/Error 状态，不修改 `components/ui`。
6. Dashboard 增加轻量 AIOps 摘要，仅在用户具备相应读取权限时查询并展示开放告警、活动
   Incident 与最近记录。

## 规范取舍

- Portal 当前没有 `components/data-table` 封装，告警与 Incident 列表沿用现有
  `AssetsTable` 的 `ResponsiveTable + TanStack Table + URL 分页` 模式，不为本次页面另建平行
  表格基础设施。
- Incident 详情按全局前端规范提供概览、时间线、AI 诊断、Runbook、自动化日志和复盘顶部
  Tabs。概览保留完整三栏工作区，时间线、AI 诊断和复盘提供聚焦视图；当前后端尚未提供
  Runbook 与自动化日志详情接口，因此对应 Tab 显式禁用，不伪造空数据或执行入口。
- Portal `knip` 门禁会检查整个应用。本次同时移除 11 个既有、无引用的公共导出；该清理不
  改变运行时行为，目的是让交付要求中的未使用代码检查真实通过。

## 验收标准

- [x] 系统管理员可在侧栏进入告警和 Incident 页面。
- [x] 告警列表能显示 open/resolved 告警、严重度、来源和发生时间。
- [x] Incident 列表能显示当前 default 租户的 open/resolved Incident，并能进入详情。
- [x] Incident 详情显示告警、时间线、Evidence、RCA 置信度/匹配规则、AI 诊断和 Report。
- [x] API 响应解析失败、401/403、空数据和移动窄屏均有明确状态，不出现空白页面。
- [x] Portal format、lint、typecheck、test、knip、build 全部通过。
- [x] 本地 Portal 接入真实服务器 API，复验 Dashboard、告警、Incident 列表与 Z9 Incident 详情。
- [ ] 合并发布后在线复验同一组页面与真实数据。

## 非目标

- 不在本次新增 Runner/Ansible 执行 UI 或改变审批安全链路。
- 不新增后端 API、数据库 migration 或改变 Zabbix 对外监听策略。
- 不把工作记录首页改造成复杂监控大屏；只增加 AIOps 入口与必要摘要。

## 风险与回滚

前端镜像与 App 同包发布。若 Portal 回归，回滚到前一份 Core 镜像/备份即可，数据库无需
回滚；新增路由不改变既有 API 合约。

## 测试与验证

单元与浏览器组件测试覆盖：

- API 包络解析、畸形响应拒绝、可选派生资源空态与 mutation 路径。
- 告警和 Incident 的组合筛选、分页与列表渲染。
- Incident 三栏详情完整渲染，以及服务端成功后才显示动作成功提示。
- 导航权限过滤、Dashboard AIOps 指标汇总和路由权限守卫。
- 真实登录、JWT 权限与租户、Dashboard 到告警/Incident/详情的跨页 Playwright E2E。

实现验收结果：Portal 完整测试通过 `113` 个测试文件、`462` 个测试；format、lint、
typecheck、knip 和 build 均通过。真实服务器 `default` 租户包含 `1` 个数据源、`1` 个资产、
`12` 条告警（`4 open / 8 resolved`）和 `3` 个 Incident（`1 active / 2 terminal`）。

浏览器复验覆盖桌面端 `6` 个页面和 `390px` 移动端 `4` 个页面；所有页面根容器横向溢出为
`0`，移动端 `scrollWidth = clientWidth = 390`，Console error、Page error 与失败 API 请求均为
空。代表 Incident 为 `inc_0b9d0aca0e24492ebe4e8f26f57422bb`。

合并后由 Release Verify 发布 Core，再以服务器真实数据复验 Dashboard、`/alerts`、
`/incidents` 和上述 Incident 详情；发布结果记录在对应 PR 与 workflow run 中。
