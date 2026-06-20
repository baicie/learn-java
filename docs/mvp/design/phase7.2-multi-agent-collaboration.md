# Phase7.2 Multi-Agent Collaboration

## 目标

在同一个 Python Agent Runtime 内引入多角色协作：

- Evidence Agent
- RCA Agent
- Runbook Agent
- Safety Agent
- Reviewer Agent

## 不做

- 不做多进程 Agent
- 不做多服务 Agent
- 不做真实 LLM 自动辩论
- 不调 Runner
- 不创建 execution
- 不创建 automation_plan
- 不自动修复
- 不自动回滚
- 不做长期 Memory

## Graph

```
input
-> evidence_graph
-> case_retrieval_graph
-> multi_agent_rca_node
   -> EvidenceAgent
   -> RCAAgent
-> human_checkpoint_graph
-> multi_agent_recommendation_node
   -> RunbookAgent
   -> SafetyAgent
   -> ReviewerAgent
-> final_report_graph
```

## Request Flag

```json
{
  "enable_multi_agent_collaboration": true
}
```

默认 false，保证兼容原单 Agent graph。

## Output

DiagnosisResponse 新增：

```json
{
  "agent_messages": []
}
```

每条 message 包含：

- message_id
- role
- title
- content
- confidence
- metadata

## 安全边界

多 Agent 仍然只输出建议。

不得创建 automation_plan。
不得创建 execution。
不得调用 runner。
不得直接 webhook / ansible / ssh。
不得 rollback。

## 验收标准

1. enable_multi_agent_collaboration=false 时走原 graph。
2. enable_multi_agent_collaboration=true 时产生 agent_messages。
3. EvidenceAgent 生成 evidence review message。
4. RCAAgent 生成 root cause proposal。
5. RunbookAgent 生成 advisory candidate。
6. SafetyAgent 过滤危险 action。
7. ReviewerAgent 生成 final reviewer summary。
8. HITL approved resume 后仍然支持 multi-agent recommendation。
9. 不新增执行能力。
