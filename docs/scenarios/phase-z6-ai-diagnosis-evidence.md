# Phase Z6: Evidence-based AI Diagnosis

## Goal

Phase Z6 lets AI Diagnosis consume deterministic evidence and RCA results.

Java sends:

```txt
incident
alerts
diagnosis_evidence
latest RCA
timeline
```

Python Agent returns:

```txt
summary
impact
timeline
rootCause
nextSteps
runbookSuggestions
evidenceRefs
matchedRules
```

## Boundary

Rules and collectors produce facts.

LLM or deterministic agent only summarizes, explains, and recommends actions.

## Deterministic Mode

For local tests:

```bash
AIOPS_AGENT_GENERATION_MODE=deterministic
```

Expected output:

```txt
summary:
  order-service 在故障窗口内同时出现 CPU 持续高位、接口响应变慢和健康检查失败

rootCause:
  疑似主机 CPU 资源持续饱和，导致服务处理能力下降
```

## Request / Response Contract

### Request (Java → Python)

`AgentDiagnosisRequest` (10 fields):

```json
{
  "contractVersion": "agent-diagnosis.v1",
  "tenantId": "tenant_1",
  "incidentId": "inc_1",
  "incident": { "id": "inc_1", "title": "...", ... },
  "alerts": [...],
  "rca": {
    "id": "rca_1",
    "suspectedRootCause": "CPU saturation",
    "matchedRules": ["CPU_API_HEALTH_COMBINED"],
    "evidenceRefs": ["evd_cpu", "evd_api", "evd_health"]
  },
  "evidence": [
    {
      "evidenceKey": "evd_cpu",
      "evidenceType": "metric_cpu_high",
      "title": "CPU 使用率持续高位",
      "summary": "CPU 最大值 96%"
    }
  ],
  "timeline": [...],
  "locale": "zh-CN",
  "traceId": "uuid"
}
```

### Response raw (Python → Java)

The `raw` field includes evidence-aware metadata:

```json
{
  "generationMode": "deterministic-evidence",
  "evidenceRefs": ["evd_cpu", "evd_api", "evd_health"],
  "matchedRules": ["CPU_API_HEALTH_COMBINED"],
  "timeline": [
    {
      "time": "2026-06-21T05:00:00",
      "type": "metric_cpu_high",
      "title": "CPU 使用率持续高位",
      "summary": "CPU 最大值 96%",
      "evidenceRef": "evd_cpu"
    }
  ],
  "diagnosisSections": {
    "summary": "...",
    "rootCause": "...",
    "impact": "...",
    "nextSteps": ["..."]
  }
}
```

## Verify

```bash
curl -X POST "http://localhost:8080/api/incidents/<incidentId>/ai-diagnosis" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant_default" \
  -d '{"force": true, "locale": "zh-CN"}'
```

## Data Flow

```txt
Incident
  -> Alerts
  -> Evidence (diagnosis_evidence table)
  -> RCA (rules-v2-evidence)
  -> AI Diagnosis (deterministic)
```

At this point, AI Diagnosis no longer generates text from alert titles alone.
It produces explainable conclusions based on Zabbix evidence and rule RCA output.
