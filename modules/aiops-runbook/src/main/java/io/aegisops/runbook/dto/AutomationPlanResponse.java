package io.aegisops.runbook.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record AutomationPlanResponse(
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
    List<AutomationPlanStepResponse> steps,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
