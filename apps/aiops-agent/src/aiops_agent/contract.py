from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from fastapi import HTTPException, status

from aiops_agent.schemas import DiagnoseRequest, DiagnoseResponse
from aiops_agent.settings import Settings


def contract_root() -> Path:
    current = Path(__file__).resolve()

    for parent in current.parents:
        candidate = parent / "contracts" / "agent"
        if candidate.exists():
            return candidate

        candidate = parent.parent / "contracts" / "agent"
        if candidate.exists():
            return candidate

    fallback = current.parents[4] / "contracts" / "agent"
    return fallback


def load_contract_schema(name: str) -> dict[str, Any]:
    path = contract_root() / name
    return json.loads(path.read_text(encoding="utf-8"))


def validate_request_contract(request: DiagnoseRequest, settings: Settings) -> None:
    if request.contractVersion != settings.contract_version:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"unsupported contractVersion: {request.contractVersion}",
        )

    if not request.traceId.strip():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="traceId is required",
        )

    if request.incident.id != request.incidentId:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="incident.id must equal incidentId",
        )


def validate_response_contract(response: DiagnoseResponse, settings: Settings) -> None:
    if response.contractVersion != settings.contract_version:
        raise RuntimeError(f"invalid response contractVersion: {response.contractVersion}")

    required_text = {
        "provider": response.provider,
        "model": response.model,
        "agentName": response.agentName,
        "summary": response.summary,
        "rootCause": response.rootCause,
        "impact": response.impact,
    }

    for name, value in required_text.items():
        if value is None or not str(value).strip():
            raise RuntimeError(f"response.{name} is required")

    if response.nextSteps is None:
        raise RuntimeError("response.nextSteps is required")
    if response.runbookSuggestions is None:
        raise RuntimeError("response.runbookSuggestions is required")
    if response.risks is None:
        raise RuntimeError("response.risks is required")
    if response.raw is None:
        raise RuntimeError("response.raw is required")
