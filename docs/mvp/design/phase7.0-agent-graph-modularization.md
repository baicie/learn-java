---
title: Phase7.0 Agent Graph Modularization
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---
# Phase7.0 Agent Graph Modularization

## 目标

将原本单体 Diagnosis Agent 拆成可维护、可测试、可扩展的 Graph 子模块。

## 不做

- 不做多 Agent
- 不做人审 checkpoint
- 不做 Agent Memory
- 不调 Runner
- 不做自动执行
- 不做自动回滚

## 子图

- evidence_graph
- case_retrieval_graph
- rca_graph
- runbook_graph
- safety_graph
- final_report_graph

## 主流程

```
input
-> evidence_graph
-> case_retrieval_graph
-> rca_graph
-> runbook_graph
-> safety_graph
-> final_report_graph
-> DiagnosisResponse
```

## 安全边界

Agent 输出仍然只是诊断建议。

- 不得直接生成 execution。
- 不得直接调用 runner。
- 不得直接执行 webhook / ansible / ssh。
- 不得直接 rollback。

## 验收标准

1. /v1/diagnose contract 不破坏。
2. graph_version = phase7.0-modular-graph。
3. evidence_graph 可独立测试。
4. case_retrieval_graph 可独立测试。
5. rca_graph 可独立测试。
6. runbook_graph 可独立测试。
7. safety_graph 可独立测试。
8. final_report_graph 可独立测试。
9. orchestrator 能串联所有子图。
10. 不引入执行能力。
