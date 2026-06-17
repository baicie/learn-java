package io.aegisops.runbook.dto;

public record AlertForPlanRecord(
    String id,
    String severity,
    String title,
    String description,
    String assetId,
    String entityName,
    String fingerprint,
    String labelsJson) {}
