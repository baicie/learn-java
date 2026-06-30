---
title: RCA Engine Design
type: ai
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related:
  - .agents/skills/aegisops/SKILL.md
  - docs/data-model.md
  - docs/ai-agent-design.md
---

# RCA Engine Design

# RCA Engine Design

> **单一真相源：`.agents/skills/aegisops/SKILL.md` §11** — 本文件为顶层入口；详细规则输出格式
> 与评分策略以 SKILL 为准。

## 1. 设计总则（指向 SKILL §11）

```txt
MVP 阶段 RCA = 规则化 + 证据链 + AI 摘要
禁止：MVP 阶段训练模型
禁止：在基础规则未跑通前做复杂图算法
```

## 2. MVP 规则清单（SKILL §11）

| 规则 ID | 名称                   | 命中条件                            |
| ------- | ---------------------- | ----------------------------------- |
| R1      | 最近变更窗口           | incident 起始 ±30 分钟内有变更      |
| R2      | 同资产多告警           | 同一 asset_id 出现 ≥2 告警          |
| R3      | 上游→下游告警时序      | upstream asset 告警先于下游         |
| R4      | 单 host / service 集中 | 故障集中在单一 host/service/version |
| R5      | metric × log 关联      | 指标异常时间窗与日志错误重叠        |
| R6      | 历史相似事故           | pgvector 命中近邻 incident          |
| R7      | Runbook 匹配           | 内置/自定义 Runbook 命中 pattern    |
| R8      | alert storm            | fingerprint 短时间内重复            |

## 3. 规则输出契约（SKILL §11）

每条规则命中必须输出 6 字段：

```txt
- rule_id       # R1 ~ R8
- score         # 0.0 ~ 1.0
- evidence      # 引用真实采集数据，禁止凭空
- related_asset_id
- related_event_id
- explanation   # 自然语言说明，给 AI 摘要使用
```

**禁止只返回 score，必须 explainable。**

## 4. 与 AI Agent 的边界

- RCA 规则引擎输出**结构化证据**，不是自然语言结论
- AI Agent 仅做**摘要 + 建议**，必须基于已采集证据
- AI 不能凭空捏造数据（SKILL §10）
- AI 输出仍按 DiagnosisResult 结构化（SKILL §6.11）

详见 `docs/ai-agent-design.md`。

## 5. 相关模块

- `modules/aiops-rca/` — 规则执行器
- `modules/aiops-evidence/` — 证据采集（Zabbix / VictoriaMetrics / Change）
- `modules/aiops-ai-client/` — AI 诊断契约与 HTTP 客户端
