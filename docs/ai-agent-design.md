---
title: AI Agent Design
type: ai
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related:
  - .agents/skills/aegisops/SKILL.md
  - docs/rca-design.md
  - docs/automation-safety.md
---

# AI Agent Design

# AI Agent Design

> **单一真相源：`.agents/skills/aegisops/SKILL.md` §10** — 本文件为顶层入口；详细 LLM provider
> 抽象、tool registry、prompt 规则以 SKILL 为准。

## 1. AI 能做什么（SKILL §10.1）

```txt
- summarize Incident
- explain evidence
- generate investigation steps
- recommend Runbook
- match historical incidents
- generate postmortem draft
- generate safe query suggestions
- explain risk
```

## 2. AI 不能直接做什么（SKILL §10.1）

```txt
- execute SSH
- execute Ansible
- delete files
- rollback production
- modify config
- restart database
- stop core middleware
- close incidents without user confirmation
```

AI 不直接操作生产；要执行任何动作，必须通过 AutomationJob 走 aiops-runner。

## 3. LLM Provider 抽象（SKILL §10.2）

```java
public interface LlmProvider {
    ChatResult chat(ChatRequest request);
    EmbeddingResult embed(EmbeddingRequest request);
}
```

支持实现：

```txt
- OpenAiProvider
- DeepSeekProvider
- QwenProvider
- OllamaProvider（本地 sidecar）
```

业务层禁止硬编码厂商；环境变量切换即可。

## 4. Tool Registry（SKILL §10.3）

允许工具：

```txt
queryMetrics / queryLogs / queryAlerts / queryAssets
queryTopology / queryChanges / searchRunbooks
searchSimilarIncidents / generateIncidentReport
recommendRunbook / proposeAutomation
```

禁止工具：

```txt
executeCommand / executeShell
restartServiceDirectly / deleteFileDirectly
modifyProductionConfigDirectly
```

执行链必须经过 `proposeAutomation → AutomationJob → approval → aiops-runner`。

## 5. Diagnosis Prompt 规则（SKILL §10.4）

Prompt 必须包含：

```txt
- Incident basic info
- related alerts
- asset info
- metric context
- timeline events
- RCA rule evidence
- historical similar incidents
- available runbooks
- automation policy constraints
```

AI 必须输出（结构化）：

```txt
summary / impact / suspected root cause
confidence / evidence
recommended next steps
recommended runbooks
automation risk warning
```

低置信度时必须使用 `疑似 / 可能 / 根据当前证据 / 需要进一步确认` 等措辞（**禁止编造确定性**）。

## 6. aiops-agent 边界

详细边界由 ADR 0003 给出（PR9 待办）。MVP 阶段假设 aiops-agent 是与 aiops-server 1:1 部署的
sidecar，仅暴露 OpenAI 兼容 HTTP 接口，不直接访问 PostgreSQL / MinIO。
