package io.aegisops.runbook.dto;

import java.math.BigDecimal;

public record AutomationPlanCreateCommand(
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
    String createdBy) {}
