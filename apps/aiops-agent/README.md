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
