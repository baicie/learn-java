package io.aegisops.audit;

import io.aegisops.common.id.Ids;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core audit recorder. The {@link #record(AuditRecordCommand)} method is transactional so that an
 * audit row is written in the same transaction as the business mutation, guaranteeing that a
 * successful business write always produces an audit event.
 */
@Service
public class AuditService {
  private final AuditRepository repository;
  private final AuditJson auditJson;

  public AuditService(AuditRepository repository, AuditJson auditJson) {
    this.repository = repository;
    this.auditJson = auditJson;
  }

  @Transactional
  public AuditEvent record(AuditRecordCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("audit command is required");
    }

    requireText(command.tenantId(), "tenantId");
    requireText(command.action(), "action");
    requireText(command.resourceType(), "resourceType");
    requireText(command.resourceId(), "resourceId");

    AuditEvent event =
        new AuditEvent(
            Ids.newId(),
            command.tenantId(),
            actorOrSystem(command.actorId()),
            command.action(),
            command.resourceType(),
            command.resourceId(),
            auditJson.normalizeObject(command.beforeJson(), "beforeJson"),
            auditJson.normalizeObject(command.afterJson(), "afterJson"),
            auditJson.normalizeObject(command.detailJson(), "detailJson"),
            command.requestId(),
            command.ip(),
            command.userAgent(),
            OffsetDateTime.now(ZoneOffset.UTC));

    repository.insert(event);
    return event;
  }

  private String actorOrSystem(String actorId) {
    return actorId == null || actorId.isBlank() ? "system" : actorId;
  }

  private void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
  }
}
