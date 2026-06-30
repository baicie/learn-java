---
title: Phase7.3 Agent Memory
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---
# Phase7.3 Agent Memory

## 目标

为 Agent 提供受控、可审计、可检索的记忆能力。

## 不做

- 不记任意内容
- 不记隐私
- 不记密钥
- 不记原始日志全文
- 不跨租户共享
- 不创建 execution
- 不调 runner
- 不自动修复
- 不自动 rollback

## Memory Types

- incident_summary
- root_cause_pattern
- service_behavior
- runbook_hint
- safety_note

## Java Tables

- agent_memory
- agent_memory_event

## Python Graph

```
input
-> memory_retrieval_graph
-> evidence_graph
-> case_retrieval_graph
-> rca / multi_agent_rca
-> human_checkpoint_graph
-> runbook / multi_agent_recommendation
-> final_report_graph
-> memory_write_graph
-> output
```

## Request Flags

```json
{
  "enable_agent_memory": true,
  "enable_agent_memory_write": true
}
```

## 写入策略

Memory 只有满足以下条件才写入：

1. enable_agent_memory_write=true
2. confidence >= 0.60
3. root_cause 已确认
4. checkpoint 不处于 pending/rejected/failed
5. 内容不包含 password/token/secret/private key
6. content 长度不超过 4000

## 安全边界

Agent Memory 只影响诊断建议，不触发执行。
