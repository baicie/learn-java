import pytest

from aiops_agent.schemas import DiagnoseRequest, IncidentContext
from aiops_agent.settings import Settings
from aiops_agent.trace import AgentTracer


def request() -> DiagnoseRequest:
    return DiagnoseRequest(
        contractVersion="agent-diagnosis.v1",
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(id="inc_1"),
        traceId="trace_1",
    )


def test_tracer_wrap_records_success_step():
    tracer = AgentTracer(Settings(), request())

    def node(state):
        return {"ok": True}

    wrapped = tracer.wrap("node_a", "node", node)
    result = wrapped({"request": request()})
    trace = tracer.finish("completed", "provider", "model", "", {})

    assert result == {"ok": True}
    assert trace["runId"].startswith("run_")
    assert trace["traceId"] == "trace_1"
    assert trace["steps"][0]["stepName"] == "node_a"
    assert trace["steps"][0]["status"] == "completed"
    assert trace["steps"][0]["durationMs"] >= 0


def test_tracer_wrap_records_failed_step():
    tracer = AgentTracer(Settings(), request())

    def node(state):
        raise RuntimeError("boom")

    wrapped = tracer.wrap("node_b", "node", node)

    with pytest.raises(RuntimeError):
        wrapped({"request": request()})

    trace = tracer.finish("failed", "provider", "model", "boom", {})

    assert trace["steps"][0]["status"] == "failed"
    assert "boom" in trace["steps"][0]["errorMessage"]


def test_disabled_tracer_returns_empty_trace():
    tracer = AgentTracer(Settings(), request(), enabled=False)

    trace = tracer.finish("completed", "provider", "model", "", {})

    assert trace == {}
