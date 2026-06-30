---
title: aiops-agent（Python LangGraph 运行时）的边界与可见性
type: adr
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related:
  - .agents/skills/aegisops/SKILL.md
  - docs/fixes/phase-5/2026-06-29-second-review-remediation.md
  - docs/ai-agent-design.md
  - docs/mvp/design/phase4.1.md
  - .agents/skills/aegisops/SKILL.md §10
---

# ADR 0003: aiops-agent（Python LangGraph 运行时）的边界与可见性

## 状态

- **已接受（2026-06-30）**
- 触发来源：第二轮回盘点出 P2（`docs/fixes/phase-5/2026-06-29-second-review-remediation.md` §10）
- 落地 PR：PR9（本文 ADR）
- 注意：`second-review-remediation.md` §10 修复方案写的是「LLM 本地推理 sidecar（Ollama / vLLM）」，经代码核查后**为错误描述**，本 ADR 基于实际代码更正

---

## 背景

`apps/aiops-agent/` 是仓库中唯一一个 Python 应用，与 Java 为主的后端栈不同。SKILL.md §10「AI Agent Rules」仅定义了 AI 行为的**接口规则**（tool registry、prompt 约束、provider 抽象），**没有说明** aiops-agent 本身的：

- 部署形态
- 与三 app（server / worker / runner）的通信方式
- 内部 token 安全边界
- 演进路径

---

## 事实（代码核查结论）

### 是什么

`apps/aiops-agent/` 是一个 **Python FastAPI + LangGraph 多 Agent 协作运行时**，版本 0.7.3。

```text
apps/aiops-agent/src/aiops_agent/
  main.py                    # FastAPI 入口，含 /health /diagnose /contract
  service.py                 # DiagnosisService
  settings.py                # Pydantic Settings，所有环境变量前缀 AIOPS_AGENT_
  schemas.py                 # FastAPI Pydantic 请求/响应模型
  contract.py                 # contract version 校验
  llm.py                     # LLM 调用（OpenAI / Azure OpenAI / Mock）
  tools.py                   # 工具注册表
  evidence.py                # 证据工具
  safety.py                  # 安全约束
  trace.py                   # 追踪
  graph.py                   # LangGraph diagnosis graph
  workflow/                  # Phase 7 模化 graph（orchestrator / evidence / rca / runbook / reviewer / safety）
  observability/             # logging / metrics / middleware
```

**aiops-agent 不是 LLM 推理服务**。它本身**依赖外部 LLM**（OpenAI / Azure OpenAI / OpenAI-compatible，如 Ollama、vLLM、DeepSeek）作为推理底座，提供的是**诊断工作流编排**能力。

### 如何与 Java 栈通信

```
aiops-server (Java)
    │
    │  HTTP POST /api/incidents/{id}/ai/diagnose
    │  header: X-AegisOps-Internal-Token: {AIOPS_AGENT_INTERNAL_AGENT_TOKEN}
    │  header: X-AegisOps-Contract-Version: agent-diagnosis.v1
    ▼
aiops-agent (Python FastAPI)
    │
    │  内部调用（可选）
    ▼
evidence-service (Java, via evidence_client)
```

**`modules/aiops-ai-client`**（Java）是 aiops-agent 的 HTTP 客户端，通过 `HttpAiAgentClient`（RestTemplate）调用 `/diagnose` 接口，`AgentContractValidator` 校验 contract version。

**aiops-agent 不直接访问 PostgreSQL / MinIO / ClickHouse**，仅通过内部 token 调用 evidence-client 访问 evidence-service。

### 部署形态

**开发 / Docker Compose**：`infra/docker-compose.yml` 已声明 `aegisops-agent` 服务，端口 9008，profile 包含 `ai`。

**K8s / Helm**：`deploy/helm/aegisops/values.yaml` 已声明 `agent` 子 chart，replicaCount=1，port=8000，resources requests 100m/256Mi limits 1CORE/512Mi。

### 版本与契约

| 项目             | 值                                                                        |
| ---------------- | ------------------------------------------------------------------------- |
| contract version | `agent-diagnosis.v1`                                                      |
| agent name       | `aegisops_diagnosis_graph`                                                |
| generation mode  | `deterministic`（默认）、`mock`、`openai-compatible`                      |
| internal token   | `AIOPS_AGENT_INTERNAL_AGENT_TOKEN`（dev 默认 `dev-internal-agent-token`） |

### 与 SKILL §10 的关系

SKILL §10「AI Agent Rules」是**行为规范**，定义 AI 能/不能做什么、tool registry、prompt 规则。本 ADR 是**部署与边界规范**，两者互补。

---

## 决策

### D1：保留 aiops-agent 作为独立 Python 应用

**状态**：采纳

aiops-agent 与 Java 栈通过 HTTP + internal token 解耦，好处：

- AI 推理底座（LLM Provider）可以独立演进（换 OpenAI → Azure → 本地 Ollama 不动 Java 代码）
- LangGraph workflow DSL 在 Python 生态更自然
- 独立版本发布、镜像构建、资源配额

### D2：aiops-agent 不直接访问主数据库

**状态**：采纳

aiops-agent 只能通过 `evidence_client`（带 internal token）访问 evidence-service，**禁止直接 SQL 连接** PostgreSQL。边界由 internal token 守门。

### D3：aiops-agent 版本契约稳定性要求

**状态**：采纳

`contract_version = agent-diagnosis.v1` 固化在 `settings.py`。Java 端 `AgentContractValidator` 校验版本号，不匹配拒绝请求。版本破坏性变更走独立 ADR。

### D4：aiops-agent 不参与调度执行

**状态**：采纳

aiops-agent **不触发** `aiops-runner` 执行自动化动作。它只做诊断（summarize / RCA / suggest runbook），执行决策由 `aiops-server` 根据诊断结果做出。执行路径：`aiops-server → aiops-runner → ansible`，**不经过** aiops-agent。

---

## 不纳入本 ADR 的范围

以下内容已在其他文档覆盖，不在本 ADR 重复：

- AI tool registry 规范 → SKILL.md §10.3
- LLM provider 抽象 → SKILL.md §10.2
- Prompt 约束 → SKILL.md §10.4
- Phase 4.1 contract 版本校验设计 → `docs/mvp/design/phase4.1.md`
- Phase 7 modular workflow 内部设计 → `apps/aiops-agent/src/aiops_agent/workflow/`

---

## 参考

- SKILL.md §10「AI Agent Rules」
- `docs/ai-agent-design.md`（AI Agent 顶层入口，引用 SKILL §10）
- `docs/mvp/design/phase4.1.md`（Phase 4.1 contract 与安全边界设计）
- `apps/aiops-agent/README.md`
- `apps/aiops-agent/src/aiops_agent/settings.py`
- `infra/docker-compose.yml`（aiops-agent 服务定义）
- `deploy/helm/aegisops/values.yaml`（agent chart）
