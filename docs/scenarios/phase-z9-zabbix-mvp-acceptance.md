---
title: Phase Z9：Zabbix MVP 端到端验收
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-08-02
related:
  - docs/api/operations-ingestion.md
  - docs/integrations/zabbix-webhook.md
  - scripts/demo/setup-zabbix-demo.py
  - apps/aiops-server/src/test/java/io/aegisops/server/acceptance/MultiSourceIncidentAcceptanceIT.java
---

# Phase Z9：Zabbix MVP 端到端验收

## 目标

Phase Z9 固化 Zabbix 主机与服务异常诊断 MVP 的完整链路。

2026-07-17 在不改变原 Zabbix MVP 安全边界的前提下，增加 Kubernetes、OpenTelemetry、RUM 和变更事件作为同一 Incident 证据链的扩展来源。

```text
Zabbix trapper / history.push
  -> Zabbix trigger / problem
  -> aegisops-app Worker runtime scheduled polling
  -> Alert Event
  -> Incident
  -> Evidence
  -> RCA
  -> AI Diagnosis
  -> Markdown Report
```

Webhook 是可选的低延迟接入模式，不是本地真实 Zabbix 验收的前置条件。默认验收路径由 Worker 周期轮询所有 `active` Zabbix 数据源，避免本地环境必须额外配置 Media Type、用户媒介和 Action。

多来源扩展链路：

```text
Kubernetes Inventory ─┐
OTLP Trace/Log/Metric ├─> Asset / Evidence ─> 确定性跨源 RCA
RUM Error/Web Vitals ─┤
Git/CI Change ────────┘
                              ↓
                 AI 建议 → 人工审批 → Runner → Postmortem
```

## 验收范围

| 阶段 | 能力                | 验收方式                               |
| ---- | ------------------- | -------------------------------------- |
| Z2   | Webhook ingest      | mock webhook payload                   |
| Z3   | Alert 聚合 Incident | aggregate API                          |
| Z4   | Evidence Collector  | mock Zabbix item/history/event/trigger |
| Z5   | RCA 规则            | evidence-driven RCA                    |
| Z6   | AI Diagnosis        | deterministic/mock agent               |
| Z7   | Markdown Report     | report API                             |
| Z8   | 前端页面            | console smoke test                     |

## 本地真实 Zabbix Demo

该路径把模拟指标推入真实 Zabbix 7.0，由 Zabbix 自己完成 trigger 求值与 problem 创建，再由 Worker 摄取。它不启动额外业务应用，也不直接向 AegisOps 伪造 webhook。

前置条件：

```text
1. 使用 `pnpm dev` 启动基础设施、aegisops-app（内含 Worker runtime）和 Portal。
2. 在 Portal 创建 Zabbix 数据源，endpoint 为 http://localhost:8081，凭据为本地 Zabbix 凭据。
3. 对数据源执行连接测试，使其状态成为 active；周期同步只处理 active 数据源。
```

初始化宿主、trapper item、trigger，并推送健康基线：

```bash
python scripts/demo/setup-zabbix-demo.py --action setup
```

注入完整故障：

```bash
# Linux / macOS / Git Bash
bash scripts/demo/inject-zabbix-incident.sh incident

# Windows PowerShell
python scripts/demo/setup-zabbix-demo.py --action incident
```

脚本在返回前会确认 `history.push` 的每个 item 均被接受，并等待 CPU、接口延迟、健康检查和错误计数 trigger 进入 PROBLEM。之后等待 Worker 的同步与聚合周期，在 Portal 查看 Alert 与 Incident。

恢复健康状态：

```bash
bash scripts/demo/inject-zabbix-incident.sh recover
# 或
python scripts/demo/setup-zabbix-demo.py --action recover
```

可用 action：

```text
setup | incident | recover | reset | cpu | slow | health-down | error | state
```

`state` 只查询已存在的 Demo host、trigger 和 problem，不创建或更新 Zabbix 配置。若尚未初始化，先执行 `--action setup`。

可通过 `AIOPS_ZABBIX_URL`、`AIOPS_ZABBIX_USERNAME`、`AIOPS_ZABBIX_PASSWORD`、`AIOPS_ZABBIX_DEMO_HOST` 和 `AIOPS_ZABBIX_TRAPPER_HOSTS` 覆盖本地默认值。Zabbix 7.0 `history.push` 要求 trapper item 的 Allowed hosts 同时包含 API 调用方经过 NAT 后的来源地址和 Zabbix Web 前端地址；本地 Compose 脚本会自动写入 Docker 网络 gateway 与 `zabbix-web` 容器 IP。其它部署应把 `AIOPS_ZABBIX_TRAPPER_HOSTS` 设置为实际客户端出口地址和 Web 前端 IP、受限网段或 DNS 名称，不要使用 `0.0.0.0/0`。

## 模拟 Webhook 合约脚本

以下脚本直接调用 AegisOps Webhook 和后续 API，用于快速验证 HTTP 契约。它们不会经过真实 Zabbix trigger/problem，也不能替代上一节的真实链路验收。

### Node.js（推荐，Windows / Linux / macOS 通用）

```bash
# 需要 Node.js 18+
node scripts/demo-zabbix-scenario.mjs
```

环境变量（可选）：

```bash
AIOPS_BASE_URL=http://localhost:8080 \
AIOPS_USERNAME=admin \
AIOPS_PASSWORD=admin123 \
AIOPS_ZABBIX_ENDPOINT=http://localhost:8081/api_jsonrpc.php \
AIOPS_ZABBIX_USERNAME=Admin \
AIOPS_ZABBIX_PASSWORD=zabbix \
node scripts/demo-zabbix-scenario.mjs
```

### Bash（Linux / macOS / Git Bash）

```bash
chmod +x scripts/demo-zabbix-scenario.sh
./scripts/demo-zabbix-scenario.sh
```

前置条件：

```
1. aiops-server 已启动（端口 8080）
2. PostgreSQL / Redis 等基础依赖已启动
3. aiops-agent 已启动并通过健康检查（端口 9008）
4. 默认租户 admin 用户存在（admin / admin123）
```

脚本会自动：登录获取 JWT、定位或创建 Zabbix datasource、执行连接测试使其成为 active、通过
受保护 API 领取 datasource-scoped token，再注入模拟 Webhook、运行聚合和后续流程。脚本不
读取全局 Webhook token，也不会把服务端签名 secret 作为请求 token。

定位已有数据源时，脚本同时精确匹配 `type=zabbix` 与 `AIOPS_ZABBIX_ENDPOINT`；同租户存在
其它 Zabbix endpoint 时不会误用。Node.js 请求默认 30 秒超时，可通过
`AIOPS_HTTP_TIMEOUT_MS` 调整；Bash 请求分别受 `AIOPS_HTTP_CONNECT_TIMEOUT_SECONDS` 与
`AIOPS_HTTP_TIMEOUT_SECONDS` 限制。任一请求超时、HTTP 非成功响应，或 Evidence、RCA、AI
Diagnosis、Markdown Report 返回空结构时，脚本都会非零退出。本次四条 Webhook 事件共用同一
`startsAt`，避免运行跨越 10 分钟聚合窗口时被拆成多个 Incident。

## 期望输出

脚本应输出：

```
Webhook CPU High: ok
Webhook API Slow: ok
Webhook Health Check Failed: ok
Webhook Error Log Increased: ok

Aggregate Incident:
  存在一个 Incident，精确包含本次四条注入事件
  可复用已存在的 Incident，不要求 incidentsCreated >= 1

Collect Evidence:
  evidenceCreated + evidenceUpdated >= 1

Run RCA:
  suspectedRootCause 非空
  matchedRules 非空

Run AI Diagnosis:
  diagnosis 非空；失败时脚本非零退出

Generate Markdown Report:
  markdownContent 包含 故障报告 / 关键证据 / AI 诊断
```

## 测试文件

| 文件                                      | 说明                       |
| ----------------------------------------- | -------------------------- |
| `DefaultZabbixClientPhaseZ9MockTest.java` | Zabbix API Mock 测试       |
| `PhaseZ9ZabbixMvpFlowTest.java`           | 端到端集成测试             |
| `PhaseZ9RcaEvidenceRulesTest.java`        | RCA 规则回归测试           |
| `test_phase_z9_mock_diagnosis.py`         | Python Agent Mock 诊断测试 |
| `test_setup_zabbix_demo.py`               | Trapper 注入脚本单元测试   |
| `PhaseZ9ConsoleSmoke.test.tsx`            | 前端 Smoke 测试            |
| `MultiSourceIncidentAcceptanceIT.java`    | 多来源 PostgreSQL 验收     |

多来源验收额外断言：重复摄取幂等、跨租户 DataSource 不可用、Kubernetes 资源关系稳定、RUM 用户只存 hash、Trace/Log/Metric/RUM/Change 均可按 Incident 时间窗回查。AI 仍不能直接调用 Runner，审批和审计沿用原 Z9 执行链测试。

## 最终 MVP 定义

完成 Z9 后，系统达到：

```
最小化 Zabbix 主机与服务异常诊断 MVP
```

用户可以按顺序完成：

```
看到告警
  -> 聚合故障
  -> 查看证据
  -> 规则 RCA
  -> AI 诊断
  -> 生成报告
```

## 常见问题

### 1. Webhook 返回 Invalid Zabbix webhook token

先确认数据源已通过连接测试且状态为 `active`，然后在 Portal 重新下载并导入当前数据源的
Webhook 模板。若服务端签名 secret 曾变化，旧模板中的 datasource-scoped token 会立即失效。

长期运行或多副本部署必须固定同一个随机签名 secret，例如：

```bash
export AIOPS_INTEGRATIONS_ZABBIX_WEBHOOK_TOKEN="$(openssl rand -base64 32)"
```

该值只配置在 aiops-server，不能直接填入 Media Type；实际 token 由
`GET /api/datasources/{id}/zabbix-webhook-token` 领取。

### 2. 真实注入后没有 Alert 或 Incident

先运行 `--action state` 只读确认 Zabbix trigger 已进入 PROBLEM，再确认数据源状态为 `active`、aegisops-app 健康且 `AIOPS_ZABBIX_SYNC_ENABLED` 未被关闭。App 内 Worker runtime 默认每 60 秒为符合条件的数据源排队一次同步任务。

### 3. AI Diagnosis 失败

完整 Z9 闭环把 AI Diagnosis 作为必经阶段；agent 未配置或调用失败时，验收脚本会非零退出，
不得继续把报告生成视为本次验收通过。先确认 `aiops-agent` 健康且服务端 AI endpoint 配置正确，
再重新执行场景。
