# contracts — 接口契约与 JSON Schema

本目录是 AegisOps **对外接口契约**与 **AI 工具契约**的单一真相源。

## 原则

```text
- 所有跨进程（前端 ↔ 后端、Server ↔ Worker ↔ Runner、外部 LLM ↔ Server）的数据交换
  必须有 JSON Schema 定义
- Schema 变更必须向后兼容（禁止删除已有字段、禁止改已有字段类型）
- 前后端类型定义优先从 contracts/ 派生，禁止手写重复 interface
- examples/ 目录下存放代表性请求/响应示例，用于调试与 mock
```

## 目录结构

```text
contracts/
├─ agent/                          # AI Agent 工具契约（SKILL.md §10）
│  ├─ diagnosis-request.schema.json
│  ├─ diagnosis-response.schema.json
│  └─ examples/
│     ├─ diagnosis-request.example.json
│     └─ diagnosis-response.example.json
│
└─ <future>/
   ├─ zabbix/                     # Zabbix webhook → Server 事件归一化契约
   ├─ vm/                         # VictoriaMetrics → Server 指标契约
   ├─ clickhouse/                 # ClickHouse → Server 日志契约
   ├─ runbook/                    # Runbook 结构化执行结果契约
   └─ automation/                 # AutomationJob 状态变更契约
```

## 当前已定义的契约

### agent/diagnosis-request.schema.json

AI 诊断请求格式。对应 SKILL.md §6.11「DiagnosisResult 必须结构化」。

关键字段：

```json
{
  "incidentId": "string (UUID)",
  "alertEvents": "AlertEvent[]",
  "assets": "Asset[]",
  "metrics": "MetricPoint[]",
  "logs": "LogLine[]",
  "timeline": "IncidentTimeline[]",
  "options": {
    "maxTokens": "number (optional, default 4096)",
    "temperature": "number (optional, default 0.3)"
  }
}
```

### agent/diagnosis-response.schema.json

AI 诊断结果格式。

关键字段：

```json
{
  "incidentId": "string (UUID)",
  "rootCause": "string",
  "confidence": "number (0-1)",
  "evidence": "EvidenceRef[]",
  "recommendations": "Recommendation[]",
  "confidenceLevel": "HIGH | MEDIUM | LOW"
}
```

## 新增契约流程

1. 在对应子目录新建 `<name>.schema.json`
2. 在 `examples/` 目录新建 `<name>.example.json`
3. 更新本文档「当前已定义的契约」章节
4. 前端/后端从 schema 生成 TypeScript / Java 类型
5. 如涉及 SKILL.md §10 变更，同步更新 AI tool registry 文档

## 禁止事项

- 禁止在代码里硬编码与 schema 不一致的 interface
- 禁止跳过 schema 直接传 `Map<String, Object>` / `any`
- 禁止在 `examples/` 放入真实生产数据
