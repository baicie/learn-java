---
title: 修复 Zabbix 真实端到端链路
type: fix
status: accepted
phase: phase-1
owner: ai
created: 2026-07-27
updated: 2026-07-29
related:
  - docs/scenarios/phase-z9-zabbix-mvp-acceptance.md
  - docs/integrations/zabbix-webhook.md
  - .agents/skills/aegisops/references/architecture-boundaries.md
---

# 修复 Zabbix 真实端到端链路

## Problem

本地 Zabbix 容器可以单独存活，但数据库等依赖在 Docker Desktop 重启后保持停止，形成
`zabbix-server` 运行、`zabbix-web` unhealthy 的半活状态。即使手工恢复容器，仓库中的真实
Demo 仍引用已删除的 `demo-order-service:8088`，平台也只有手工同步任务，没有持续摄取。

此外，Webhook 仍按旧的 `asset.source_id=datasourceId:hostId` 查资产，Worker 聚合只扫描最近
30 分钟开始的告警，都会在真实 Zabbix Problem 链路中丢失关联或 Incident。轮询只处理当前
Problem，恢复事件、重复轮询幂等、同步任务并发认领和 Webhook datasource 级鉴权也未形成
可重复验收的完整契约。

## Impact

- Docker Desktop 重启后 Zabbix 页面和 API 不可用。
- Zabbix 新 Problem 不会自动进入 AegisOps，必须人工点击同步。
- 已持续超过 30 分钟的 open Problem 能生成 AlertEvent，但不会生成 Incident。
- Webhook AlertEvent 无法关联由当前资产同步模型创建的 Asset。
- Demo 故障注入脚本请求不存在的 8088 服务，无法用于验收。
- 短时故障可能在聚合前已恢复，导致 Incident 永久缺失。
- 重复轮询会重复写 Incident 聚合 outbox；恢复后迟到的旧 PROBLEM 可能回滚告警终态。
- 同一数据源的重复/重试同步任务可能并发执行，旧 Worker 还能覆盖新任务结果。

## Root Cause

1. Compose 中长期依赖容器缺少一致的 restart policy。
2. `setup-zabbix-demo.py` 的参数定义与实现漂移，且 HTTP Agent/Web Scenario 仍依赖已删除应用。
3. Worker 没有 active Zabbix 数据源的周期调度入口。
4. 自动聚合把故障发生时间当作待处理任务的时间下限。
5. Webhook 未迁移到 `asset_source_link` 权威来源关系。
6. Polling 未读取 recovery event，Alert upsert 也没有终态单调保护。
7. 同步 run 缺少 lease、claim token 和 datasource 级串行化。
8. Webhook 使用全局明文 token，无法限定数据源，演示脚本也绕过真实领取流程。
9. Node/Bash 合约脚本只判断 HTTP 2xx，未限制请求时长，也未验证 Evidence、RCA、AI 与报告的
   业务内容；多数据源场景还会误用第一条 Zabbix 数据源。

## Fix Plan

1. 通过测试固定 source-link 资产解析和长时间 open Problem 聚合语义。
2. 在 datasource application facade 中创建到期的 scheduled sync run 与幂等 outbox，Worker
   只负责按配置周期触发该 facade。
3. 将 Demo 改为 Zabbix trapper item；使用 Zabbix 7.0 `history.push` 注入和恢复指标，让
   `problem.get -> Worker sync -> AlertEvent -> Incident` 成为真实路径。
4. 为长期容器补 `restart: unless-stopped`，统一 Runner 本地端口为 8092。
5. 归一 polling recovery、tags、severity、fingerprint、aggregation key 与 raw payload；保证
   resolved Alert/Incident 状态单调，并让短时故障也能创建后立即关闭 Incident。
6. 为 datasource sync run 增加 lease、claim token、fencing 与 datasource 行锁，覆盖重投、
   过期接管和 pending 优先级。
7. Webhook 统一走 Alert application facade；使用 datasource-scoped HMAC token、active tenant
   绑定、敏感领取审计和 `Cache-Control: no-store`。
8. 更新 Phase Z9 验收文档并执行真实 Docker/Zabbix/Server/Worker 验证。
9. 加固 Node/Bash 合约脚本：精确匹配 Zabbix endpoint、固定运行级事件时间、设置 HTTP 超时，
   并把下游空响应视为验收失败。

## Files to Change

- `modules/aiops-asset/**`：source-link 查询 facade 与单元测试。
- `modules/aiops-integration/**`：Webhook 改用资产查询 facade。
- `modules/aiops-alert/**`：终态单调 upsert 与生命周期 outbox 幂等。
- `modules/aiops-incident/**`、`apps/aiops-worker/**`：无窗口自动聚合与周期同步调度。
- `modules/aiops-datasource/**`：scheduled dispatch、sync lease/claim/fencing 与 recovery 映射。
- `apps/aiops-server/src/main/resources/db/migration/V0047__init_datasource_sync_run_lease.sql`：
  同步任务 lease 与 fencing token。
- `apps/aiops-server/src/main/resources/db/migration/V0048__init_outbox_replay_intent.sql`：
  outbox 处理中重放意图与 claim 清理。
- `apps/aiops-server/src/test/**/PhaseZ9ZabbixMvpFlowTest.java`：PostgreSQL 回归覆盖。
- `scripts/demo/**`：trapper 配置、故障注入、脚本契约测试。
- `infra/docker-compose.yml`、`infra/env.example`、`scripts/start*`：运行时恢复与端口修正。
- `docs/scenarios/phase-z9-zabbix-mvp-acceptance.md`、`docs/integrations/zabbix-webhook.md`：
  真实链路与可选 Webhook 说明。

## Implementation Details

- 所有 source-link 查询包含 `tenantId`、`sourceType`、`sourceInstanceId`、`externalId`。
- 手工 aggregate API 保留 lookback 语义；Worker 使用独立的“未关联 open/resolved alerts”
  入口并按 limit 分批，避免任何有限时间窗造成永久遗漏，也覆盖聚合前已恢复的短时故障。
- 周期调度只选 `status=active AND type=zabbix` 且到期、没有 pending/running run 的数据源。
  调度写入 `sync_type=scheduled`，outbox 使用时间槽幂等键并与 run 同事务提交。
- Zabbix 外部 IO 仍只由 `aiops-zabbix-adapter` 和 Demo 配置脚本承担；不新增第四个 Java app。
- Webhook 与 polling 共用 `AlertIngestService`，Alert upsert 与 outbox 同事务；outbox 以
  `alertId + persistedStatus` 做生命周期幂等，重复 open 不放大，recovery 仍产生一次新事件。
- resolved Alert 拒绝迟到 open 覆盖状态、恢复时间、证据 payload、fingerprint 与聚合键。
- `datasource_sync_run` 新增 `lease_until`、`claim_token`，完成/失败必须携带 claim token，
  旧 Worker 丢失 claim 后不能覆盖新执行结果。
- 新增 datasource-scoped Webhook token API；签名 secret 只注入 aiops-server，生产部署必须
  显式配置稳定随机值，token 仅通过 Header 使用且领取动作写审计但不记录 token。
- Node.js 和 Bash 合约脚本只复用 `type=zabbix` 且 endpoint 与
  `AIOPS_ZABBIX_ENDPOINT` 完全一致的数据源；所有四条注入事件共用同一 `startsAt`。Node.js
  默认 30 秒请求超时，Bash 同时设置连接与总超时。
- Evidence 必须满足 `evidenceCreated + evidenceUpdated >= 1`，RCA 必须返回根因与命中规则，
  AI 必须返回 summary/root cause/impact，报告必须有 ID 且包含故障报告、关键证据和 AI 诊断。

## Tests

- Asset query facade 精确委托 source-link 四元组。
- Webhook AlertEvent 关联同步模型下的 canonical Asset。
- Worker job 调用无窗口聚合入口；真实 PostgreSQL 覆盖旧 `starts_at` open alert。
- 周期调度仅排队到期 active Zabbix 数据源，并避免未完成任务重复入队。
- PostgreSQL 并发回归覆盖同 datasource 的 failed retry 竞争、pending 优先级与 fencing。
- 重复 polling 只保留一条 open 生命周期 outbox，recovery 追加一条 resolved 生命周期 outbox。
- Webhook token 覆盖租户/类型/权限、active tenant、Header-only、no-store 与无敏感审计。
- Demo Python 单测覆盖 trapper 配置、incident/recover `history.push` 与 CLI 参数。
- Node/Bash 合约测试覆盖 endpoint 精确匹配、统一事件时间、请求超时及下游空响应失败语义。
- 启动脚本测试覆盖 Runner 8092；Compose 配置校验覆盖 restart policy。

## Verification Commands

```bash
mvn -B -ntp -pl modules/aiops-asset,modules/aiops-integration,modules/aiops-incident,apps/aiops-worker,apps/aiops-server -am test
node --import tsx --test scripts/start.test.ts
python -m unittest scripts/demo/test_setup_zabbix_demo.py
pnpm exec tsx scripts/docs.ts check
bash scripts/ci/backend.sh
bash scripts/ci/docs.sh
bash scripts/ci/verify-local.sh
```

真实验收使用现有持久卷启动 Compose，配置 Zabbix Demo host，注入 trapper 值，确认 Worker
自动同步后数据库中存在带 Asset 的 AlertEvent、Incident 与对应 outbox 完成记录。

## Acceptance Evidence

2026-07-29 使用现有持久卷完成真实 Zabbix 7.0 problem/recovery 验收。AegisOps 数据库时间为
UTC，因此以下记录日期显示为 2026-07-28。租户为
`b4ea8fde329b4e7fb9beab550aaccc6d`，数据源为
`ds_d583a05e70ff4590ac1f015ae5ee26fb`。

三个 Java 应用健康检查均返回 `UP`，Flyway `V0047`、`V0048` 均为 `success=true`。
执行 `python scripts/demo/setup-zabbix-demo.py --action incident` 后，真实 Zabbix 生成四个互不相同的
problem event `40`、`41`、`42`、`43`；执行 `--action recover` 后 open problem 为 0。

Alert 的 problem/recovery、资产关联与终态查询：

```sql
select id as alert_id, source_event_id, status, severity, asset_id,
       raw_payload->>'eventid' as problem_event_id,
       raw_payload->>'r_eventid' as recovery_event_id,
       starts_at, ends_at
from alert_event
where source_event_id in (
  'ds_d583a05e70ff4590ac1f015ae5ee26fb:40',
  'ds_d583a05e70ff4590ac1f015ae5ee26fb:41',
  'ds_d583a05e70ff4590ac1f015ae5ee26fb:42',
  'ds_d583a05e70ff4590ac1f015ae5ee26fb:43'
)
order by (raw_payload->>'eventid')::bigint;
```

| alert_id                           | problem_event_id | recovery_event_id | status   | severity | asset_id                                 |
| ---------------------------------- | ---------------- | ----------------- | -------- | -------- | ---------------------------------------- |
| `c983601b2d204798884cc25a1d15b623` | 40               | 44                | resolved | high     | `asset_bc21be455e61486788567987da372aeb` |
| `7df66885ef274d2bb32e217fc4a754bd` | 41               | 45                | resolved | medium   | `asset_bc21be455e61486788567987da372aeb` |
| `6fc9630d09b8468391225bd8d9cab86c` | 42               | 46                | resolved | critical | `asset_bc21be455e61486788567987da372aeb` |
| `79145cfa193943de8b79b7b9ec4dee24` | 43               | 47                | resolved | medium   | `asset_bc21be455e61486788567987da372aeb` |

四条 Alert 的 `starts_at` 均为 `2026-07-28 19:35:53+00`，`ends_at` 均为
`2026-07-28 19:38:48+00`。恢复事件取自 Zabbix 原始字段 `r_eventid`，四个值均非空且互不相同。

Incident 精确关联与终态查询：

```sql
select i.id, i.status, i.severity, i.alert_count, i.primary_asset_id,
       count(ie.event_id) filter (where ie.event_type='alert') as linked_alerts,
       i.started_at, i.resolved_at
from incident i
left join incident_event ie on ie.incident_id=i.id
where i.id='inc_26c0c8e69f5944798d0584c62f97bf44'
group by i.id;
```

结果为 `status=resolved`、`severity=critical`、`alert_count=4`、`linked_alerts=4`，
`primary_asset_id=asset_bc21be455e61486788567987da372aeb`，`resolved_at` 为
`2026-07-28 19:38:48+00`。

生命周期 outbox 查询：

```sql
select id, status, idempotency_key, payload->>'alertId' as alert_id,
       retry_count, processed_at
from automation_outbox
where job_name='incident-aggregate'
  and payload->>'alertId' in (
    'c983601b2d204798884cc25a1d15b623',
    '7df66885ef274d2bb32e217fc4a754bd',
    '6fc9630d09b8468391225bd8d9cab86c',
    '79145cfa193943de8b79b7b9ec4dee24'
  )
order by created_at;
```

结果共 8 条：四条 `alert-lifecycle:<alertId>:open` 与四条
`alert-lifecycle:<alertId>:resolved` 均为 `done`，`retry_count=0`，对应 outbox ID 为：

```text
outbox_4bd936563cac4cf0aeda867bb42b51b1
outbox_10de3ee0724d4989ad3f1bc80fde8c35
outbox_121c9c10cfcd43c2b497c30fe0ed003c
outbox_cc70484ce3b64014b3f506e36c9cccbe
outbox_a070d8f866ea421993e1f83b402da97b
outbox_5029a95f9bf74710950777d534d2208a
outbox_3f5bc65d7afa4bb380258c8812230abf
outbox_f3302ff05ee541c2a1b773882c986841
```

定时同步快照查询：

```sql
select id, sync_type, status, started_at, finished_at, stats_json, message
from datasource_sync_run
where datasource_id='ds_d583a05e70ff4590ac1f015ae5ee26fb'
order by started_at desc
limit 1;
```

快照结果为 `sync_be98d22593f7314203b01d072e8232ef`、`sync_type=scheduled`、
`status=success`、`message=Sync completed`。验收过程中连接测试故意与已 claim 的 scheduled run
并发，旧 run `sync_54ff7530b2c00e3658a14ff3fd192bbc` 被 fencing 拒绝写回，并在 lease 过期后标记为
`failed / Sync lease expired`；随后调度器自动接管并连续完成新的 scheduled run。

最后执行 `node scripts/demo-zabbix-scenario.mjs` 验证 Z9 后半链路，脚本退出码为 0：

```text
Incident: inc_768c0bcc90fc4a9b89edebdacaafd067
RCA: R4_SAME_ASSET_CONCENTRATION, R1_HIGH_SEVERITY, R2_ALERT_VOLUME, R6_TIMELINE_BURST
AI Diagnosis: success
Markdown Report: rpt_a366f2f4ca604a4e8f2c2ee8cfb82513
```

AI Diagnosis 返回非空 summary、root cause 与 impact，报告包含故障摘要、关键证据、RCA 和 AI
诊断。该脚本在 AI 调用失败时会非零退出，本轮没有把 AI 失败降级为验收成功。

## Rollback Plan

回滚应用提交即可恢复原行为。Compose restart policy 不改动数据卷；Demo trapper item 和 trigger
可在 Zabbix 中禁用或删除。不得执行 `docker compose down -v`。

## Follow-up

- 生产环境如需自动创建 Zabbix Media Type/Action，应单独设计权限、回滚和审计；本次只提供
  Portal 下载模板与人工导入，不自动修改 Zabbix 通知策略。
