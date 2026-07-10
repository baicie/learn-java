package io.aegisops.audit;

import io.aegisops.common.id.Ids;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
  private final AuditRepository repository;
  private final AuditJson auditJson;

  public AuditService(AuditRepository repository, AuditJson auditJson) {
    this.repository = repository;
    this.auditJson = auditJson;
  }

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
            auditJson.normalizeJson(command.beforeJson()),
            auditJson.normalizeJson(command.afterJson()),
            auditJson.normalizeJson(command.detailJson()),
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
