package io.aegisops.runbook.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AutomationPlanRecord(
    String id,
    String tenantId,
    String incidentId,
    String runbookId,
    String aiDiagnosisId,
    String rcaAnalysisId,
    String source,
    String status,
    String riskLevel,
    BigDecimal confidence,
    String title,
    String summary,
    String evidenceJson,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
