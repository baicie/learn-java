---
title: AegisOps Observability
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---
# AegisOps Observability

## Metrics

Java services expose:

- `/actuator/prometheus`

Python Agent exposes:

- `/metrics`

## Important Java Metrics

- `aegisops_http_requests_total`
- `aegisops_http_request_duration_bucket`
- `jvm_memory_used_bytes`
- `process_cpu_usage`

## Important Python Metrics

- `aiops_agent_http_requests_total`
- `aiops_agent_http_request_duration_seconds_bucket`
- `aiops_agent_diagnosis_total`
- `aiops_agent_tool_calls_total`

## Request Headers

AegisOps uses:

- `X-Request-Id`
- `X-Trace-Id`
- `X-Tenant-Id`

All internal calls should propagate request-id and trace-id.

## Alerts

- `AegisOpsHighHttp5xxRate`
- `AegisOpsHighP95Latency`
- `AegisOpsAgentHighErrorRate`
- `AegisOpsAgentDiagnosisFailures`
