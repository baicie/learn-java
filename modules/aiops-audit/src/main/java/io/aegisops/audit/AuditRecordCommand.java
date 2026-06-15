package io.aegisops.audit;

/**
 * Command for AuditService#record. Bundles the audit row fields so the service signature stays
 * within the project-wide checkstyle ParameterNumber limit (5).
 */
public record AuditRecordCommand(
    String tenantId,
    String actorUserId,
    String action,
    String targetType,
    String targetId,
    String detailJson) {}
