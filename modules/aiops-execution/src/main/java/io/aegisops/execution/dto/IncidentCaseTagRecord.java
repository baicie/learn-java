package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record IncidentCaseTagRecord(
    String id, String tenantId, String caseId, String tag, OffsetDateTime createdAt) {}
