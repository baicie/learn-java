package io.aegisops.evidence.dto;

public record EvidenceQueryResponse(
    String contractVersion,
    String tenantId,
    String incidentId,
    String traceId,
    MetricEvidence metrics,
    LogEvidence logs,
    ChangeEvidence changes) {}
