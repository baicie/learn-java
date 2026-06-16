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
    "Do not execute remediation automatically in Phase4.2.",
    "All remediation actions require human confirmation in later phases.",
]

BLOCKED_SUGGESTION_TEXT = (
    "Blocked unsafe remediation suggestion. "
    "Review manually and create an approved AutomationPlan in later phases."
)

BLOCKED_INLINE_TEXT = "[blocked unsafe remediation wording]"


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

    summary = _sanitize_text(response.summary)
    root_cause = _sanitize_text(response.rootCause)
    impact = _sanitize_text(response.impact)

    next_steps = _sanitize_suggestion_list(response.nextSteps)
    runbook_suggestions = _sanitize_suggestion_list(response.runbookSuggestions)
    risks = _sanitize_suggestion_list(response.risks)

    for risk in MANDATORY_RISKS:
        if risk not in risks:
            risks.append(risk)

    if blocked:
        warning = "Potentially unsafe remediation wording was detected and removed from the response."
        if warning not in risks:
            risks.append(warning)

    raw = dict(response.raw)
    raw["safety"] = {
        "blockedKeywords": blocked,
        "autoExecutionAllowed": False,
        "sanitized": bool(blocked),
    }

    return response.model_copy(update={
        "summary": summary,
        "rootCause": root_cause,
        "impact": impact,
        "nextSteps": next_steps,
        "runbookSuggestions": runbook_suggestions,
        "risks": risks,
        "raw": raw,
    })


def assert_safe_response(response: DiagnoseResponse) -> None:
    blocked = find_forbidden_keywords(response)
    if blocked:
        raise RuntimeError(f"unsafe auto-execution keyword detected: {', '.join(blocked)}")


def _contains_forbidden_keyword(value: str) -> bool:
    lowered = value.lower()
    return any(keyword in lowered for keyword in FORBIDDEN_AUTO_EXECUTION_KEYWORDS)


def _sanitize_text(value: str) -> str:
    sanitized = value
    for keyword in FORBIDDEN_AUTO_EXECUTION_KEYWORDS:
        sanitized = sanitized.replace(keyword, BLOCKED_INLINE_TEXT)
        sanitized = sanitized.replace(keyword.upper(), BLOCKED_INLINE_TEXT)
        sanitized = sanitized.replace(keyword.title(), BLOCKED_INLINE_TEXT)
    return sanitized


def _sanitize_suggestion_list(values: list[str]) -> list[str]:
    sanitized: list[str] = []

    for value in values:
        if _contains_forbidden_keyword(value):
            if BLOCKED_SUGGESTION_TEXT not in sanitized:
                sanitized.append(BLOCKED_SUGGESTION_TEXT)
            continue

        sanitized.append(value)

    return sanitized
