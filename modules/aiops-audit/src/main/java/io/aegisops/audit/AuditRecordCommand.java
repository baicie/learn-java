package io.aegisops.audit;

/** Command for AuditService#record. Includes before/after/detail JSON snapshots. */
public record AuditRecordCommand(
    String tenantId,
    String actorId,
    String action,
    String resourceType,
    String resourceId,
    String beforeJson,
    String afterJson,
    String detailJson) {

  /** Compatibility constructor for callers that only have a detail JSON. */
  public AuditRecordCommand(
      String tenantId,
      String actorId,
      String action,
      String resourceType,
      String resourceId,
      String detailJson) {
    this(tenantId, actorId, action, resourceType, resourceId, "{}", "{}", detailJson);
  }
}
