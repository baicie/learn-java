import pytest
from fastapi import HTTPException

from aiops_agent.contract import (
    load_contract_schema,
    validate_request_contract,
    validate_response_contract,
)
from aiops_agent.schemas import DiagnoseRequest, DiagnoseResponse, IncidentContext
from aiops_agent.settings import Settings


def test_load_contract_schema():
    schema = load_contract_schema("diagnosis-request.schema.json")

    assert schema["title"] == "AegisOps Agent Diagnosis Request"
    assert "contractVersion" in schema["required"]


def test_validate_request_contract_accepts_valid_request():
    settings = Settings(contract_version="agent-diagnosis.v1")

    request = DiagnoseRequest(
        contractVersion="agent-diagnosis.v1",
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(id="inc_1"),
        traceId="trace_1",
    )

    validate_request_contract(request, settings)


def test_validate_request_contract_rejects_wrong_version():
    settings = Settings(contract_version="agent-diagnosis.v1")

    request = DiagnoseRequest(
        contractVersion="bad",
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(id="inc_1"),
        traceId="trace_1",
    )

    with pytest.raises(HTTPException):
        validate_request_contract(request, settings)


def test_validate_response_contract_accepts_valid_response():
    settings = Settings(contract_version="agent-diagnosis.v1")

    response = DiagnoseResponse(
        contractVersion="agent-diagnosis.v1",
        provider="aiops-agent",
        model="langgraph-deterministic",
        agentName="aegisops_diagnosis_graph",
        summary="summary",
        rootCause="root",
        impact="impact",
        nextSteps=[],
        runbookSuggestions=[],
        risks=[],
        raw={},
    )

    validate_response_contract(response, settings)
