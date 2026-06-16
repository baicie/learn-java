# aiops-agent

AegisOps Python LangGraph diagnosis agent runtime.

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
