# Phase Z5: Evidence-aware RCA Rules

## Goal

Phase Z5 upgrades RCA from alert-title matching to evidence-aware rule diagnosis.

Input:

```txt
incident
alerts
assetRelations
diagnosisEvidence
```

Output:

```txt
suspectedRootCause
confidence
summary
matchedRules
evidenceRefs
evidence
```

## Rules

### HOST_CPU_HIGH_WITH_SERVICE_SLOW

Matches:

```txt
metric_cpu_high
metric_api_slow
```

Conclusion:

```txt
主机 CPU 持续高位导致服务响应变慢
```

### SERVICE_HEALTH_CHECK_FAILED

Matches:

```txt
metric_health_check_failed
optional metric_api_slow
```

Conclusion:

```txt
服务健康检查失败，并伴随接口响应变慢
```

### ERROR_LOG_INCREASED_WITH_TIMEOUT

Matches:

```txt
metric_error_log_increased
optional timeout keyword
```

Conclusion:

```txt
服务错误日志增加并出现 Timeout，疑似请求处理阻塞或下游调用超时
```

### CPU_API_HEALTH_COMBINED

Matches:

```txt
metric_cpu_high
metric_api_slow
metric_health_check_failed
```

Conclusion:

```txt
主机 CPU 持续高位导致服务响应变慢，并进一步引发健康检查失败
```

## Evidence → Rules Mapping

```txt
metric_cpu_high
  → HOST_CPU_HIGH_WITH_SERVICE_SLOW
  → CPU_API_HEALTH_COMBINED

metric_api_slow
  → HOST_CPU_HIGH_WITH_SERVICE_SLOW
  → SERVICE_HEALTH_CHECK_FAILED
  → CPU_API_HEALTH_COMBINED

metric_health_check_failed
  → SERVICE_HEALTH_CHECK_FAILED
  → CPU_API_HEALTH_COMBINED

metric_error_log_increased
  → ERROR_LOG_INCREASED_WITH_TIMEOUT

zabbix_event_timeline
  → auxiliary evidence

zabbix_trigger_expression
  → auxiliary evidence
```

## Next Phase

Phase Z6 will let AI Diagnosis consume this deterministic RCA result.
