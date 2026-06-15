from __future__ import annotations

from aiops_agent.schemas import DiagnoseResponse


FORBIDDEN_AUTO_EXECUTION_KEYWORDS = [
    "rm -rf",
    "drop database",
    "truncate table",
    "delete namespace",
    "kubectl delete",
    "format disk",
    "shutdown -h",
    "reboot now",
    "无需审批自动",
    "自动执行删除",
    "自动清空",
]

MANDATORY_RISKS = [
    "Do not execute remediation automatically in Phase4.1.",
    "All remediation actions require human confirmation in later phases.",
]


def find_forbidden_keywords(response: DiagnoseResponse) -> list[str]:
    text = "\n".join([
        response.summary,
        response.rootCause,
        response.impact,
        *response.nextSteps,
        *response.runbookSuggestions,
        *response.risks,
    ]).lower()

    return [keyword for keyword in FORBIDDEN_AUTO_EXECUTION_KEYWORDS if keyword in text]


def apply_safety_boundary(response: DiagnoseResponse) -> DiagnoseResponse:
    blocked = find_forbidden_keywords(response)

    risks = list(response.risks)
    for risk in MANDATORY_RISKS:
        if risk not in risks:
            risks.append(risk)

    raw = dict(response.raw)
    raw["safety"] = {
        "blockedKeywords": blocked,
        "autoExecutionAllowed": False,
    }

    if blocked:
        risks.append(
            "Potentially unsafe remediation wording was detected and must be reviewed manually."
        )

    return response.model_copy(update={
        "risks": risks,
        "raw": raw,
    })


def assert_safe_response(response: DiagnoseResponse) -> None:
    blocked = find_forbidden_keywords(response)
    if blocked:
        raise RuntimeError(f"unsafe auto-execution keyword detected: {', '.join(blocked)}")
