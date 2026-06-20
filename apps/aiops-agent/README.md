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
AIOPS_AGENT_INTERNAL_TOKEN=dev-internal-token
AIOPS_AGENT_MODEL=langgraph-deterministic
AIOPS_AGENT_PROVIDER=aiops-agent
AIOPS_AGENT_NAME=aegisops_diagnosis_graph
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
AIOPS_AGENT_EVIDENCE_INTERNAL_TOKEN=dev-internal-token
```

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

Checkpoint continuation uses `POST /v1/diagnose/resume` and requires the same internal
token and optional contract-version headers as `POST /v1/diagnose`.
