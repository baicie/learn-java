---
title: Phase Z7: Markdown Incident Report
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---
# Phase Z7: Markdown Incident Report

## Goal

Phase Z7 persists an incident report as Markdown.

Input:

```txt
Incident
Alerts
Diagnosis Evidence
RCA Analysis
AI Diagnosis
Incident Timeline
```

Output:

```txt
incident_report.markdown_content
```

## Generate Report

```bash
curl -X POST "http://localhost:8080/api/incidents/<incidentId>/reports" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant_default" \
  -d '{
    "force": true,
    "locale": "zh-CN",
    "createdBy": "demo"
  }'
```

## Get Latest Report

```bash
curl "http://localhost:8080/api/incidents/<incidentId>/reports/latest" \
  -H "X-Tenant-Id: tenant_default"
```

## Frontend Preview

The frontend can render:

```txt
data.markdownContent
```

with a Markdown viewer.

## Regeneration

`POST /api/incidents/{id}/reports` creates a new version.

`GET /api/incidents/{id}/reports/latest` returns the newest version.

## Report Sections

```txt
基本信息
故障摘要
影响范围
时间线
关联告警
关键证据
RCA 根因判断
AI 诊断
处理建议
```

## Complete Flow

```txt
Zabbix Alert
  -> Alert Event
  -> Incident
  -> Evidence
  -> RCA
  -> AI Diagnosis
  -> Markdown Report
```
