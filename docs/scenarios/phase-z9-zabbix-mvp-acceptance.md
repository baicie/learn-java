---
title: Phase Z9：Zabbix MVP 端到端验收
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-07-17
related:
  - docs/api/operations-ingestion.md
  - apps/aiops-server/src/test/java/io/aegisops/server/acceptance/MultiSourceIncidentAcceptanceIT.java
---

# Phase Z9：Zabbix MVP 端到端验收

## 目标

Phase Z9 固化 Zabbix 主机与服务异常诊断 MVP 的完整链路。

2026-07-17 在不改变原 Zabbix MVP 安全边界的前提下，增加 Kubernetes、OpenTelemetry、RUM 和变更事件作为同一 Incident 证据链的扩展来源。

```
Zabbix Webhook
  -> Alert Event
  -> Incident
  -> Evidence
  -> RCA
  -> AI Diagnosis
  -> Markdown Report
```

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

## 本地 Demo 脚本

### Node.js（推荐，Windows / Linux / macOS 通用）

```bash
# 需要 Node.js 18+
node scripts/demo-zabbix-scenario.mjs
```

环境变量（可选）：

```bash
AIOPS_BASE_URL=http://localhost:8080 \
AIOPS_ZABBIX_WEBHOOK_TOKEN=dev-zabbix-webhook-token \
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
3. 默认租户 admin 用户存在（admin / admin123）
```

脚本会自动：登录获取 JWT、创建 Zabbix datasource、注入 webhook、运行聚合和后续流程。

## 期望输出

脚本应输出：

```
Webhook CPU High: ok
Webhook API Slow: ok
Webhook Health Check Failed: ok
Webhook Error Log Increased: ok

Aggregate Incident:
  incidentsCreated >= 1

Collect Evidence:
  evidenceCollected >= 0

Run RCA:
  suspectedRootCause 非空
  matchedRules 非空

Run AI Diagnosis:
  可能失败（agent 未配置），不影响后续

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

检查服务端配置 `application.yml` 中是否有：

```yaml
aiops:
  integrations:
    zabbix:
      webhook:
        token: dev-zabbix-webhook-token
```

或设置环境变量 `AIOPS_INTEGRATIONS_ZABBIX_WEBHOOK_TOKEN`。

### 2. 聚合返回 0 incidents

确保告警的 `startsAt` 在近 24 小时内（聚合默认窗口 1440 分钟）。

### 3. AI Diagnosis 失败

当前 AI agent 未配置时会返回失败，不影响报告生成。配置 AI agent 后此步骤会自动通过。
