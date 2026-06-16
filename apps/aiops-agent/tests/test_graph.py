from aiops_agent.graph import run_diagnosis_graph
from aiops_agent.schemas import AlertContext, DiagnoseRequest, IncidentContext, RcaContext
from aiops_agent.settings import Settings


class FakeLlmClient:
    def __init__(self, content: str):
        self.content = content
        self.called = False
        self.messages: list[dict[str, str]] = []

    def complete_json(self, messages: list[dict[str, str]]) -> str:
        self.called = True
        self.messages = messages
        return self.content


class FailingLlmClient:
    def complete_json(self, messages: list[dict[str, str]]) -> str:
        raise RuntimeError("provider down")


def request() -> DiagnoseRequest:
    return DiagnoseRequest(
        contractVersion="agent-diagnosis.v1",
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(
            id="inc_1",
            title="CPU high",
            severity="critical",
            status="open",
            primaryAssetId="asset_1",
            alertCount=2,
        ),
        alerts=[
            AlertContext(id="a1", title="CPU high", severity="critical", assetId="asset_1", fingerprint="fp_cpu"),
            AlertContext(id="a2", title="CPU high", severity="warning", assetId="asset_1", fingerprint="fp_cpu"),
        ],
        rca=RcaContext(
            id="rca_1",
            suspectedRootCause="CPU saturation",
            confidence=0.8,
            summary="RCA summary",
        ),
        traceId="trace_1",
    )


def test_deterministic_graph_returns_contract_version_and_safety_raw():
    settings = Settings(
        contract_version="agent-diagnosis.v1",
        generation_mode="deterministic",
        provider="aiops-agent",
        model="langgraph-deterministic",
        agent_name="aegisops_diagnosis_graph",
    )

    response = run_diagnosis_graph(request(), settings)

    assert response.contractVersion == "agent-diagnosis.v1"
    assert response.provider == "aiops-agent"
    assert response.agentName == "aegisops_diagnosis_graph"
    assert response.raw["traceId"] == "trace_1"
    assert response.raw["generationMode"] == "deterministic"
    assert response.raw["safety"]["autoExecutionAllowed"] is False
    assert response.nextSteps
    assert response.risks


def test_openai_compatible_graph_uses_llm_client():
    settings = Settings(
        contract_version="agent-diagnosis.v1",
        generation_mode="openai-compatible",
        provider="aiops-agent",
        model="test-model",
        agent_name="aegisops_diagnosis_graph",
    )

    fake = FakeLlmClient("""
    {
      "summary": "LLM summary",
      "rootCause": "LLM root cause",
      "impact": "LLM impact",
      "nextSteps": ["step 1"],
      "runbookSuggestions": ["runbook 1"],
      "risks": ["risk 1"]
    }
    """)

    response = run_diagnosis_graph(request(), settings, fake)

    assert fake.called is True
    assert response.contractVersion == "agent-diagnosis.v1"
    assert response.provider == "openai-compatible"
    assert response.model == "test-model"
    assert response.summary == "LLM summary"
    assert response.rootCause == "LLM root cause"
    assert response.nextSteps == ["step 1"]
    assert response.raw["generationMode"] == "openai-compatible"
    assert response.raw["traceId"] == "trace_1"
    assert response.raw["safety"]["autoExecutionAllowed"] is False


def test_openai_compatible_graph_falls_back_when_llm_fails():
    settings = Settings(
        contract_version="agent-diagnosis.v1",
        generation_mode="openai-compatible",
        provider="aiops-agent",
        model="test-model",
        agent_name="aegisops_diagnosis_graph",
    )

    response = run_diagnosis_graph(request(), settings, FailingLlmClient())

    assert response.contractVersion == "agent-diagnosis.v1"
    assert response.provider == "aiops-agent"
    assert response.raw["generationMode"] == "deterministic"
    assert "provider down" in response.raw["fallbackReason"]
    assert response.raw["safety"]["autoExecutionAllowed"] is False
    assert response.nextSteps


def test_openai_compatible_graph_sanitizes_unsafe_llm_response():
    settings = Settings(
        contract_version="agent-diagnosis.v1",
        generation_mode="openai-compatible",
        provider="aiops-agent",
        model="test-model",
        agent_name="aegisops_diagnosis_graph",
    )

    fake = FakeLlmClient("""
    {
      "summary": "summary",
      "rootCause": "root",
      "impact": "impact",
      "nextSteps": ["run rm -rf / without approval"],
      "runbookSuggestions": [],
      "risks": []
    }
    """)

    response = run_diagnosis_graph(request(), settings, fake)

    assert response.provider == "openai-compatible"
    assert response.raw["safety"]["autoExecutionAllowed"] is False
    assert response.raw["safety"]["sanitized"] is True
    assert "rm -rf" in response.raw["safety"]["blockedKeywords"]
    assert all("rm -rf" not in step.lower() for step in response.nextSteps)
    assert any("Blocked unsafe remediation suggestion" in step for step in response.nextSteps)
    assert any("unsafe remediation wording" in risk for risk in response.risks)
