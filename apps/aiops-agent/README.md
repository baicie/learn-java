# aiops-agent

AegisOps Python LangGraph diagnosis agent runtime.

The installable runtime uses a single `src` layout:

```text
src/aiops_agent/
  main.py        # authenticated FastAPI entry point
  service.py     # canonical diagnosis service
  workflow/      # Phase 7 modular graph, tools and collaboration agents
```

## Local dev

```bash
cd apps/aiops-agent
python -m venv .venv
. .venv/Scripts/activate
pip install -e ".[test]"
uvicorn aiops_agent.main:app --reload --port 9008
```

## Test

```bash
pytest
```

## Env

```bash
AIOPS_AGENT_INBOUND_OAUTH2_ISSUER=http://localhost:8089/realms/aegisops
AIOPS_AGENT_INBOUND_OAUTH2_JWKS_URL=http://localhost:8089/realms/aegisops/protocol/openid-connect/certs
AIOPS_AGENT_INBOUND_OAUTH2_AUDIENCE=aiops-agent-api
AIOPS_AGENT_OUTBOUND_OAUTH2_TOKEN_URL=http://localhost:8089/realms/aegisops/protocol/openid-connect/token
AIOPS_AGENT_OUTBOUND_OAUTH2_CLIENT_ID=aiops-agent
AIOPS_AGENT_OUTBOUND_OAUTH2_CLIENT_SECRET=dev-aiops-agent-client-secret
AIOPS_AGENT_MODEL=langgraph-deterministic
AIOPS_AGENT_PROVIDER=aiops-agent
AIOPS_AGENT_NAME=aegisops_diagnosis_graph
```

本地 IdP 由仓库 Compose 自动初始化：

```bash
docker compose -f infra/docker-compose.yml up -d keycloak
```

## Phase4.2 OpenAI-compatible mode

Default mode is deterministic:

```bash
AIOPS_AGENT_GENERATION_MODE=deterministic
```

Enable OpenAI-compatible LLM generation:

```bash
AIOPS_AGENT_GENERATION_MODE=openai-compatible
AIOPS_AGENT_MODEL=deepseek-chat
AIOPS_AGENT_OPENAI_BASE_URL=https://api.deepseek.com/v1
AIOPS_AGENT_OPENAI_API_KEY=your-api-key
```

The LLM must return strict JSON:

```json
{
  "summary": "short incident diagnosis summary",
  "rootCause": "most likely root cause based on evidence",
  "impact": "potential impact",
  "nextSteps": ["manual troubleshooting step"],
  "runbookSuggestions": ["runbook name or direction"],
  "risks": ["risk or caveat"]
}
```

If LLM call fails or returns invalid JSON, the graph falls back to deterministic diagnosis.

Phase4.2 still does not execute remediation.

## Phase4.3 Evidence Tools

Python Agent does not connect to databases or production systems directly.

It queries Java internal evidence API:

```bash
AIOPS_AGENT_EVIDENCE_ENABLED=true
AIOPS_AGENT_EVIDENCE_BASE_URL=http://localhost:8080/internal/agent/evidence
```

服务间鉴权只使用 OAuth2 Client Credentials。Diagnosis Grant 无法通过配置关闭；诊断期间
Agent 会把 Java 签发的 `X-AegisOps-Diagnosis-Grant` 原样传播到内部工具调用。

Evidence sections:

```json
{
  "metrics": {
    "available": true,
    "series": []
  },
  "logs": {
    "available": true,
    "patterns": []
  },
  "changes": {
    "available": true,
    "events": []
  }
}
```

If evidence query fails, diagnosis still succeeds with evidence unavailable markers.

## Phase 7 workflow

Deterministic diagnosis runs through the Phase 7 modular workflow while keeping the
Java-facing `agent-diagnosis.v1` request and response contract stable. Optional internal
capabilities are configured with:

```bash
AIOPS_AGENT_WORKFLOW_API_BASE_URL=http://localhost:8080
AIOPS_AGENT_WORKFLOW_EVIDENCE_ENABLED=false
AIOPS_AGENT_WORKFLOW_CASE_RETRIEVAL_ENABLED=false
AIOPS_AGENT_WORKFLOW_HUMAN_CHECKPOINT_ENABLED=false
AIOPS_AGENT_WORKFLOW_MULTI_AGENT_ENABLED=false
AIOPS_AGENT_WORKFLOW_MEMORY_ENABLED=false
AIOPS_AGENT_WORKFLOW_MEMORY_WRITE_ENABLED=false
```

Checkpoint continuation uses `POST /v1/diagnose/resume` and requires the same OAuth2
service JWT, Diagnosis Grant, and optional contract-version headers as `POST /v1/diagnose`.

## Work-record generation with Dify

Work-record summaries and monthly reports use a capability-specific provider. The default
remains deterministic and does not require Dify:

```bash
AIOPS_AGENT_WORK_RECORD_PROVIDER=deterministic
```

Enable the Dify Workflow provider only on `aiops-agent`:

```bash
AIOPS_AGENT_WORK_RECORD_PROVIDER=dify
AIOPS_AGENT_DIFY_BASE_URL=https://dify.example.com/v1
AIOPS_AGENT_DIFY_WORK_RECORD_API_KEY=agent-only-secret
AIOPS_AGENT_DIFY_WORK_RECORD_WORKFLOW_ID=published-workflow-id
AIOPS_AGENT_DIFY_WORK_RECORD_WORKFLOW_VERSION=work-record-2026-07-19.1
AIOPS_AGENT_DIFY_TIMEOUT_SECONDS=75
AIOPS_AGENT_DIFY_MAX_RETRIES=2
AIOPS_AGENT_DIFY_MAX_INPUT_BYTES=65536
AIOPS_AGENT_DIFY_USER_HMAC_SECRET=agent-only-hmac-secret
```

`DIFY_WORK_RECORD_WORKFLOW_ID` is optional. When configured, the client calls the fixed
published workflow endpoint; otherwise it calls the app's current published workflow. The
workflow must return `markdown`, `warnings`, and the configured `workflow_version` from its
End node.

The Agent sends a blocking request because the Java work-record flow is already asynchronous.
It retries only rate limiting, explicit server errors without a known run ID, and connection
failures. A timeout may have created a remote run, so it is not retried. Invalid output or any
Dify failure returns the existing deterministic draft with an explicit warning and fallback
reason.

The serialized Dify context is limited to 64 KiB by default and excludes tenant ID, actor ID,
and trace ID. The Dify `user` value is an HMAC of tenant and actor identity. Dify API and HMAC
secrets must never be shared with the Portal, Java apps, Runner, logs, or persisted generation
metadata. Dify remains a text-generation provider: it has no database, internal API, tool, or
automation execution access, and every result remains a draft for human review.
