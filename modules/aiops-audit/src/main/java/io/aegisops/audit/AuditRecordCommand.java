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
    String detailJson,
    String requestId,
    String ip,
    String userAgent) {

  /** Compatibility constructor for callers that do not yet collect HTTP request metadata. */
  public AuditRecordCommand(
      String tenantId,
      String actorId,
      String action,
      String resourceType,
      String resourceId,
      String beforeJson,
      String afterJson,
      String detailJson) {
    this(
        tenantId,
        actorId,
        action,
        resourceType,
        resourceId,
        beforeJson,
        afterJson,
        detailJson,
        null,
        null,
        null);
  }

  /** Compatibility constructor for callers that only have a detail JSON. */
  public AuditRecordCommand(
      String tenantId,
      String actorId,
      String action,
      String resourceType,
      String resourceId,
      String detailJson) {
    this(
        tenantId,
        actorId,
        action,
        resourceType,
        resourceId,
        "{}",
        "{}",
        detailJson,
        null,
        null,
        null);
  }
}
