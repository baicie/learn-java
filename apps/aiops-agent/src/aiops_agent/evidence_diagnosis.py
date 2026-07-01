from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable

from aiops_agent.schemas import DiagnoseRequest, DiagnoseResponse, EvidenceContext
from aiops_agent.settings import settings


@dataclass(frozen=True)
class EvidenceFlags:
    cpu_high: bool
    api_slow: bool
    health_failed: bool
    error_increased: bool
    timeout_seen: bool


def evidence_types(evidence: Iterable[EvidenceContext]) -> set[str]:
    return {item.evidenceType for item in evidence if item.evidenceType}


def evidence_refs(evidence: Iterable[EvidenceContext]) -> list[str]:
    refs: list[str] = []
    for item in evidence:
        if item.evidenceKey and item.evidenceKey not in refs:
            refs.append(item.evidenceKey)
    return refs


def flags_from_request(request: DiagnoseRequest) -> EvidenceFlags:
    types = evidence_types(request.evidence)
    text = " ".join(
        [
            item.title or ""
            for item in request.evidence
        ]
        + [
            item.summary or ""
            for item in request.evidence
        ]
        + [
            item.payloadJson or ""
            for item in request.evidence
        ]
    ).lower()

    return EvidenceFlags(
        cpu_high="metric_cpu_high" in types,
        api_slow="metric_api_slow" in types,
        health_failed="metric_health_check_failed" in types,
        error_increased="metric_error_log_increased" in types,
        timeout_seen="timeout" in text,
    )


def matched_rules(request: DiagnoseRequest) -> list[str]:
    if request.rca and request.rca.matchedRules:
        return request.rca.matchedRules

    flags = flags_from_request(request)
    rules: list[str] = []

    if flags.cpu_high and flags.api_slow:
        rules.append("HOST_CPU_HIGH_WITH_SERVICE_SLOW")

    if flags.health_failed:
        rules.append("SERVICE_HEALTH_CHECK_FAILED")

    if flags.error_increased:
        rules.append("ERROR_LOG_INCREASED_WITH_TIMEOUT")

    if flags.cpu_high and flags.api_slow and flags.health_failed:
        rules.insert(0, "CPU_API_HEALTH_COMBINED")

    return list(dict.fromkeys(rules))


def build_summary(request: DiagnoseRequest, flags: EvidenceFlags) -> str:
    service = request.incident.title or "当前服务"

    if flags.cpu_high and flags.api_slow and flags.health_failed:
        return (
            f"{service} 在故障窗口内同时出现 CPU 持续高位、接口响应变慢和健康检查失败，"
            "故障具备明确的资源饱和影响服务可用性的特征。"
        )

    if flags.api_slow and flags.health_failed:
        return f"{service} 出现接口响应变慢并伴随健康检查失败，服务可用性受到影响。"

    if flags.cpu_high and flags.api_slow:
        return f"{service} 出现 CPU 使用率升高与接口响应变慢，疑似主机资源瓶颈影响服务处理能力。"

    if request.rca and request.rca.suspectedRootCause:
        return f"{service} 已完成规则 RCA 初判，结论为：{request.rca.suspectedRootCause}"

    if request.alerts:
        return f"{service} 触发 {len(request.alerts)} 条告警，但证据不足，需要继续采集指标和日志。"

    return f"{service} 暂无足够证据形成明确诊断结论。"


def build_root_cause(request: DiagnoseRequest, flags: EvidenceFlags) -> str:
    if request.rca and request.rca.suspectedRootCause:
        return request.rca.suspectedRootCause

    if flags.cpu_high and flags.api_slow and flags.health_failed:
        return "疑似主机 CPU 资源持续饱和，导致服务处理能力下降，并进一步引发健康检查失败。"

    if flags.api_slow and flags.health_failed:
        return "疑似服务处理链路异常，接口响应变慢后导致健康检查失败。"

    if flags.error_increased and flags.timeout_seen:
        return "疑似服务内部请求处理阻塞或下游调用超时，导致错误日志增加。"

    return "暂未形成高置信度根因判断，需要补充主机、接口、日志和变更证据。"


def build_impact(request: DiagnoseRequest, flags: EvidenceFlags) -> str:
    entities = [alert.entityName for alert in request.alerts if alert.entityName]
    target = entities[0] if entities else (request.incident.title or "相关服务")

    if flags.health_failed:
        return f"{target} 健康检查失败，可能影响外部请求可用性。"

    if flags.api_slow:
        return f"{target} 接口响应时间升高，用户请求可能变慢或超时。"

    return f"{target} 存在告警，需要关注潜在影响范围。"


def build_timeline(request: DiagnoseRequest) -> list[dict]:
    timeline: list[dict] = []

    for item in request.evidence:
        if item.timeRangeStart:
            timeline.append(
                {
                    "time": item.timeRangeStart.isoformat(),
                    "type": item.evidenceType,
                    "title": item.title,
                    "summary": item.summary,
                    "evidenceRef": item.evidenceKey,
                }
            )

    for item in request.timeline:
        if item.eventTime:
            timeline.append(
                {
                    "time": item.eventTime.isoformat(),
                    "type": item.eventType,
                    "title": item.title,
                    "summary": item.description,
                    "evidenceRef": item.id,
                }
            )

    return sorted(timeline, key=lambda value: value.get("time") or "")


def build_next_steps(flags: EvidenceFlags) -> list[str]:
    steps: list[str] = []

    if flags.cpu_high:
        steps.append("登录主机查看 CPU Top 进程，确认是否存在异常进程、批处理任务或死循环。")

    if flags.api_slow:
        steps.append("检查慢接口调用链路，重点关注 /api/order/create 等高延迟接口的下游依赖。")

    if flags.health_failed:
        steps.append("检查服务健康检查失败原因，确认端口、线程池、连接池和依赖服务是否正常。")

    if flags.error_increased:
        steps.append("查看故障窗口内 ERROR/Timeout 日志，定位具体异常堆栈和下游超时点。")

    steps.append("检查故障窗口前后的发布、配置变更、定时任务和流量变化。")
    steps.append("如服务持续不可用，先进行限流、扩容或重启异常实例，再进入根因复盘。")

    return list(dict.fromkeys(steps))


def deterministic_diagnose(
    request: DiagnoseRequest,
    generation_mode: str = "deterministic-evidence",
) -> DiagnoseResponse:
    flags = flags_from_request(request)
    refs = evidence_refs(request.evidence)

    if request.rca and request.rca.evidenceRefs:
        refs = list(dict.fromkeys([*request.rca.evidenceRefs, *refs]))

    rules = matched_rules(request)
    timeline = build_timeline(request)

    raw = {
        "graph": "aegisops_diagnosis_graph",
        "contractVersion": request.contractVersion,
        "traceId": request.traceId,
        "generationMode": generation_mode,
        "workflow": {
            "graphVersion": settings.workflow_graph_version,
        },
        "evidenceRefs": refs,
        "matchedRules": rules,
        "timeline": timeline,
        "diagnosisSections": {
            "summary": build_summary(request, flags),
            "rootCause": build_root_cause(request, flags),
            "impact": build_impact(request, flags),
            "nextSteps": build_next_steps(flags),
        },
    }

    return DiagnoseResponse(
        contractVersion=request.contractVersion,
        provider="aiops-agent",
        model="langgraph-deterministic",
        agentName="aegisops_diagnosis_graph",
        summary=raw["diagnosisSections"]["summary"],
        rootCause=raw["diagnosisSections"]["rootCause"],
        impact=raw["diagnosisSections"]["impact"],
        nextSteps=raw["diagnosisSections"]["nextSteps"],
        runbookSuggestions=[
            "主机 CPU 高位排查 Runbook",
            "服务健康检查失败排查 Runbook",
            "接口慢请求排查 Runbook",
        ],
        risks=[
            "当前结论基于采集到的证据生成，若证据缺失可能低估真实影响。",
            "执行重启或扩容前应确认是否存在正在进行的数据处理任务。",
        ],
        raw=raw,
    )
