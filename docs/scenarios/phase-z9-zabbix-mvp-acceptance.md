# Phase Z9：Zabbix MVP 端到端验收

## 目标

Phase Z9 固化 Zabbix 主机与服务异常诊断 MVP 的完整链路。

```txt
Zabbix Webhook
  -> Alert Event
  -> Incident
  -> Evidence
  -> RCA
  -> AI Diagnosis
  -> Markdown Report
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

## 本地测试

```bash
./scripts/verify-z9.sh
```

## 本地 Demo 脚本

前置条件：

```txt
1. aiops-server 已启动
2. PostgreSQL / Redis 等基础依赖已启动
3. 已创建 tenant 和 zabbix datasource
4. AIOPS_ZABBIX_DATASOURCE_ID 指向该 datasource
5. AIOPS_ZABBIX_WEBHOOK_TOKEN 与服务端配置一致
```

执行：

```bash
AIOPS_BASE_URL=http://localhost:8080 \
AIOPS_TENANT_ID=tenant_default \
AIOPS_ZABBIX_DATASOURCE_ID=ds_zabbix_demo \
AIOPS_ZABBIX_WEBHOOK_TOKEN=zabbix-demo-token \
./scripts/demo-zabbix-scenario.sh
```

如果接口需要 JWT：

```bash
AIOPS_TOKEN=<jwt> ./scripts/demo-zabbix-scenario.sh
```

## 期望输出

脚本应输出：

```txt
Webhook CPU High: ok
Webhook API Slow: ok
Webhook Health Check Failed: ok
Webhook Error Log Increased: ok

Aggregate Incident:
  incidentsCreated >= 1

Collect Evidence:
  evidenceCreated >= 1

Run RCA:
  suspectedRootCause 非空
  matchedRules 包含 CPU_API_HEALTH_COMBINED

Run AI Diagnosis:
  summary 非空
  rootCause 非空
  evidenceRefs 非空

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

## 最终 MVP 定义

完成 Z9 后，系统达到：

```txt
最小化 Zabbix 主机与服务异常诊断 MVP
```

用户可以按顺序完成：

```txt
看到告警
  -> 聚合故障
  -> 查看证据
  -> 规则 RCA
  -> AI 诊断
  -> 生成报告
```

## 常见问题

### 1. Webhook 返回 401 / 403

检查：

```txt
AIOPS_ZABBIX_WEBHOOK_TOKEN
服务端 webhook token 配置
/api/integrations/zabbix/** 是否已放行
```

### 2. collect evidence 为空

检查：

```txt
datasource.config_json 是否为 ZabbixConfig
Zabbix item.get 是否能返回 demo.cpu.util / demo.order.create.time / demo.health.status
alert labels 中是否包含 datasourceId / zabbixHostId / service / env
```

### 3. RCA 没有命中组合规则

检查 diagnosis_evidence 是否包含：

```txt
metric_cpu_high
metric_api_slow
metric_health_check_failed
```

### 4. AI Diagnosis 没有 evidenceRefs

检查：

```txt
AgentDiagnosisRequest 是否包含 evidence
AgentDiagnosisRequest.rca 是否包含 evidenceRefs
Python deterministic raw 是否输出 evidenceRefs
```

### 5. Report 缺少关键章节

检查：

```txt
incident_report.markdown_content
MarkdownReportRenderer 是否包含 Summary / Alerts / Evidence / RCA / AI Diagnosis / Report sections
```
