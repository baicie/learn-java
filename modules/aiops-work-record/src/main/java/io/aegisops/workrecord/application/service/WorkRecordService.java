package io.aegisops.workrecord.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.CreateRecordCommand;
import io.aegisops.workrecord.application.command.UpdateRecordCommand;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.application.port.WorkRecordRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateVersionRepository;
import io.aegisops.workrecord.application.port.WorkRecordUserPort;
import io.aegisops.workrecord.domain.model.RecordStatus;
import io.aegisops.workrecord.domain.model.WorkRecord;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.domain.model.WorkRecordTemplateVersion;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkRecordService {
  private final WorkRecordRepository recordRepository;
  private final WorkRecordTemplateVersionRepository versionRepository;
  private final WorkRecordFieldIndexRepository fieldRepository;
  private final WorkRecordValueValidator valueValidator;
  private final WorkRecordAuditService auditService;
  private final WorkRecordPermissionService permissionService;
  private final WorkRecordUserPort userPort;
  private final ObjectMapper objectMapper;

  public WorkRecordService(
      WorkRecordRepository recordRepository,
      WorkRecordTemplateVersionRepository versionRepository,
      WorkRecordFieldIndexRepository fieldRepository,
      WorkRecordValueValidator valueValidator,
      WorkRecordAuditService auditService,
      WorkRecordPermissionService permissionService,
      WorkRecordUserPort userPort,
      ObjectMapper objectMapper) {
    this.recordRepository = recordRepository;
    this.versionRepository = versionRepository;
    this.fieldRepository = fieldRepository;
    this.valueValidator = valueValidator;
    this.auditService = auditService;
    this.permissionService = permissionService;
    this.userPort = userPort;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public WorkRecord create(String tenantId, CreateRecordCommand command, UserPrincipal user) {
    if (command == null) {
      throw new IllegalArgumentException("record request is required");
    }
    requireText(tenantId, "tenantId");
    requireText(command.templateId(), "templateId");
    requireText(command.templateVersionId(), "templateVersionId");
    requireText(command.title(), "title");
    requireRecordTime(command.recordTime());

    WorkRecordTemplateVersion version =
        resolveVersion(tenantId, command.templateId(), command.templateVersionId());

    List<WorkRecordField> fields = fieldRepository.listByVersion(tenantId, version.id());
    String builtin = normalizeObject(command.builtinDataJson());
    String custom = normalizeObject(command.customDataJson());
    valueValidator.validate(tenantId, version.id(), fields, custom);

    validateOwner(tenantId, command.ownerId());

    permissionService.requireCreate(user);

    CreateRecordCommand normalized =
        new CreateRecordCommand(
            command.templateId(),
            version.id(),
            command.title(),
            RecordStatus.from(command.status()).value(),
            blankToNull(command.ownerId()),
            command.recordTime(),
            builtin,
            custom);

    String actorId = actorId(user);
    WorkRecord record = recordRepository.create(tenantId, normalized, actorId);
    auditService.record(
        tenantId,
        record.id(),
        record.templateId(),
        "work_record",
        record.id(),
        "work_record.record.create",
        actorId,
        "{}");
    return record;
  }

  @Transactional
  public WorkRecord update(
      String tenantId, String recordId, UpdateRecordCommand command, UserPrincipal user) {
    if (command == null) {
      throw new IllegalArgumentException("update request is required");
    }

    WorkRecord existing =
        recordRepository
            .find(tenantId, recordId)
            .orElseThrow(() -> new IllegalArgumentException("work record not found"));

    permissionService.requireWrite(user, existing);

    String builtin =
        command.builtinDataJson() == null ? null : normalizeObject(command.builtinDataJson());
    String custom =
        command.customDataJson() == null ? null : normalizeObject(command.customDataJson());

    if (custom != null) {
      List<WorkRecordField> fields =
          fieldRepository.listByVersion(tenantId, existing.templateVersionId());
      valueValidator.validate(tenantId, existing.templateVersionId(), fields, custom);
    }

    if (command.recordTime() != null) {
      requireRecordTime(command.recordTime());
    }

    validateOwner(tenantId, command.ownerId());

    UpdateRecordCommand normalized =
        new UpdateRecordCommand(
            command.title(),
            command.status() == null ? null : RecordStatus.from(command.status()).value(),
            blankToNull(command.ownerId()),
            command.recordTime(),
            builtin,
            custom);

    WorkRecord updated = recordRepository.update(tenantId, recordId, normalized);
    auditService.record(
        tenantId,
        updated.id(),
        updated.templateId(),
        "work_record",
        updated.id(),
        "work_record.record.update",
        actorId(user),
        "{}");
    return updated;
  }

  @Transactional
  public void delete(String tenantId, String recordId, UserPrincipal user) {
    WorkRecord existing =
        recordRepository
            .find(tenantId, recordId)
            .orElseThrow(() -> new IllegalArgumentException("work record not found"));

    permissionService.requireDelete(user, existing);

    recordRepository.softDelete(tenantId, recordId);
    auditService.record(
        tenantId,
        existing.id(),
        existing.templateId(),
        "work_record",
        existing.id(),
        "work_record.record.delete",
        actorId(user),
        "{}");
  }

  public WorkRecord get(String tenantId, String recordId) {
    return recordRepository
        .find(tenantId, recordId)
        .orElseThrow(() -> new IllegalArgumentException("work record not found"));
  }

  private WorkRecordTemplateVersion resolveVersion(
      String tenantId, String templateId, String templateVersionId) {
    return versionRepository
        .findByTemplateAndVersion(tenantId, templateId, templateVersionId)
        .orElseThrow(() -> new IllegalArgumentException("template version not found"));
  }

  private String normalizeObject(String json) {
    try {
      JsonNode node = objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
      if (!node.isObject()) {
        throw new IllegalArgumentException("json must be object");
      }
      return objectMapper.writeValueAsString(node);
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid json object", ex);
    }
  }

  private void validateOwner(String tenantId, String ownerId) {
    if (ownerId == null || ownerId.isBlank()) {
      return;
    }
    userPort.requireActiveUser(tenantId, ownerId);
  }

  private void requireRecordTime(OffsetDateTime value) {
    if (value == null) {
      throw new IllegalArgumentException("recordTime is required");
    }
  }

  private void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private String actorId(UserPrincipal user) {
    return user == null || user.id() == null || user.id().isBlank() ? "system" : user.id();
  }
}