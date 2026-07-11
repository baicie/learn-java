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
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkRecordService {
  private final WorkRecordRepository recordRepository;
  private final WorkRecordTemplateVersionRepository versionRepository;
  private final WorkRecordFieldIndexRepository fieldRepository;
  private final WorkRecordValueValidator valueValidator;
  private final WorkRecordAuditService auditService;
  private final WorkRecordAuditSnapshots auditSnapshots;
  private final WorkRecordPermissionService permissionService;
  private final WorkRecordUserPort userPort;
  private final ObjectMapper objectMapper;

  public WorkRecordService(
      WorkRecordRepository recordRepository,
      WorkRecordTemplateVersionRepository versionRepository,
      WorkRecordFieldIndexRepository fieldRepository,
      WorkRecordValueValidator valueValidator,
      WorkRecordAuditService auditService,
      WorkRecordAuditSnapshots auditSnapshots,
      WorkRecordPermissionService permissionService,
      WorkRecordUserPort userPort,
      ObjectMapper objectMapper) {
    this.recordRepository = recordRepository;
    this.versionRepository = versionRepository;
    this.fieldRepository = fieldRepository;
    this.valueValidator = valueValidator;
    this.auditService = auditService;
    this.auditSnapshots = auditSnapshots;
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

    // 必须在任何 Repository 查询前完成权限判断，
    // 避免只读用户探测模板版本是否存在。
    permissionService.requireCreate(user);

    RecordStatus targetStatus = RecordStatus.from(command.status());

    WorkRecordTemplateVersion version =
        resolveVersion(tenantId, command.templateId(), command.templateVersionId());

    List<WorkRecordField> fields = fieldRepository.listByVersion(tenantId, version.id());
    String builtin = normalizeObject(command.builtinDataJson());
    String custom = normalizeObject(command.customDataJson());
    valueValidator.validate(
        tenantId, version.id(), fields, custom, targetStatus != RecordStatus.DRAFT);

    validateOwner(tenantId, command.ownerId());

    CreateRecordCommand normalized =
        new CreateRecordCommand(
            command.templateId(),
            version.id(),
            command.title(),
            targetStatus.value(),
            blankToNull(command.ownerId()),
            command.recordTime(),
            builtin,
            custom);

    String actorId = actorId(user);
    WorkRecord record = recordRepository.create(tenantId, normalized, actorId);
    auditService.recordChange(
        tenantId,
        record.id(),
        record.templateId(),
        "work_record",
        record.id(),
        WorkRecordAuditActions.RECORD_CREATE,
        actorId,
        Map.of(),
        auditSnapshots.record(record),
        Map.of());
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

    permissionService.requireEdit(user, existing);

    String builtin =
        command.builtinDataJson() == null ? null : normalizeObject(command.builtinDataJson());
    String custom =
        command.customDataJson() == null ? null : normalizeObject(command.customDataJson());

    RecordStatus targetStatus =
        command.status() == null ? existing.status() : RecordStatus.from(command.status());

    boolean statusChanged = targetStatus != existing.status();

    if (custom != null || statusChanged) {
      List<WorkRecordField> fields =
          fieldRepository.listByVersion(tenantId, existing.templateVersionId());

      String effectiveCustom = custom == null ? existing.customDataJson() : custom;

      valueValidator.validate(
          tenantId,
          existing.templateVersionId(),
          fields,
          effectiveCustom,
          targetStatus != RecordStatus.DRAFT);
    }

    if (command.recordTime() != null) {
      requireRecordTime(command.recordTime());
    }

    validateOwner(tenantId, command.ownerId());

    UpdateRecordCommand normalized =
        new UpdateRecordCommand(
            command.title(),
            command.status() == null ? null : targetStatus.value(),
            blankToNull(command.ownerId()),
            command.recordTime(),
            builtin,
            custom);

    WorkRecord updated = recordRepository.update(tenantId, recordId, normalized);
    auditService.recordChange(
        tenantId,
        updated.id(),
        updated.templateId(),
        "work_record",
        updated.id(),
        WorkRecordAuditActions.RECORD_UPDATE,
        actorId(user),
        auditSnapshots.record(existing),
        auditSnapshots.record(updated),
        Map.of());
    return updated;
  }

  @Transactional
  public void delete(String tenantId, String recordId, UserPrincipal user) {
    WorkRecord existing =
        recordRepository
            .find(tenantId, recordId)
            .orElseThrow(() -> new IllegalArgumentException("work record not found"));

    permissionService.requireDelete(user, existing);

    WorkRecord deleted = recordRepository.softDelete(tenantId, recordId);

    auditService.recordChange(
        tenantId,
        existing.id(),
        existing.templateId(),
        "work_record",
        existing.id(),
        WorkRecordAuditActions.RECORD_DELETE,
        actorId(user),
        auditSnapshots.record(existing),
        auditSnapshots.record(deleted),
        Map.of("softDelete", true));
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
