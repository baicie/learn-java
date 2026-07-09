package io.aegisops.workrecord.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.command.CreateRecordCommand;
import io.aegisops.workrecord.application.command.UpdateRecordCommand;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.application.port.WorkRecordRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateVersionRepository;
import io.aegisops.workrecord.domain.model.RecordStatus;
import io.aegisops.workrecord.domain.model.WorkRecord;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.domain.model.WorkRecordTemplateVersion;
import io.aegisops.workrecord.domain.rule.WorkRecordValueValidator;
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
  private final ObjectMapper objectMapper;

  public WorkRecordService(
      WorkRecordRepository recordRepository,
      WorkRecordTemplateVersionRepository versionRepository,
      WorkRecordFieldIndexRepository fieldRepository,
      WorkRecordValueValidator valueValidator,
      WorkRecordAuditService auditService,
      ObjectMapper objectMapper) {
    this.recordRepository = recordRepository;
    this.versionRepository = versionRepository;
    this.fieldRepository = fieldRepository;
    this.valueValidator = valueValidator;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public WorkRecord create(String tenantId, CreateRecordCommand command, String creatorId) {
    requireText(command.templateId(), "templateId");
    requireText(command.title(), "title");

    WorkRecordTemplateVersion version =
        resolveVersion(tenantId, command.templateId(), command.templateVersionId());

    List<WorkRecordField> fields = fieldRepository.listEnabledByVersion(tenantId, version.id());
    String builtin = normalizeObject(command.builtinDataJson());
    String custom = normalizeObject(command.customDataJson());
    valueValidator.validate(tenantId, fields, custom);

    CreateRecordCommand normalized =
        new CreateRecordCommand(
            command.templateId(),
            version.id(),
            command.title(),
            RecordStatus.from(command.status()).value(),
            command.ownerId(),
            command.recordTime() == null ? OffsetDateTime.now() : command.recordTime(),
            builtin,
            custom);

    WorkRecord record = recordRepository.create(tenantId, normalized, creatorId);
    auditService.record(
        tenantId,
        record.id(),
        record.templateId(),
        "work_record",
        record.id(),
        "work_record.record.create",
        creatorId,
        "{}");
    return record;
  }

  @Transactional
  public WorkRecord update(
      String tenantId, String recordId, UpdateRecordCommand command, String actor) {
    WorkRecord existing =
        recordRepository
            .find(tenantId, recordId)
            .orElseThrow(() -> new IllegalArgumentException("work record not found"));

    String builtin =
        command.builtinDataJson() == null ? null : normalizeObject(command.builtinDataJson());
    String custom =
        command.customDataJson() == null ? null : normalizeObject(command.customDataJson());

    if (custom != null) {
      List<WorkRecordField> fields =
          fieldRepository.listEnabledByVersion(tenantId, existing.templateVersionId());
      valueValidator.validate(tenantId, fields, custom);
    }

    UpdateRecordCommand normalized =
        new UpdateRecordCommand(
            command.title(),
            command.status() == null ? null : RecordStatus.from(command.status()).value(),
            command.ownerId(),
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
        actor,
        "{}");
    return updated;
  }

  @Transactional
  public void delete(String tenantId, String recordId, String actor) {
    WorkRecord existing =
        recordRepository
            .find(tenantId, recordId)
            .orElseThrow(() -> new IllegalArgumentException("work record not found"));
    recordRepository.softDelete(tenantId, recordId);
    auditService.record(
        tenantId,
        existing.id(),
        existing.templateId(),
        "work_record",
        existing.id(),
        "work_record.record.delete",
        actor,
        "{}");
  }

  public WorkRecord get(String tenantId, String recordId) {
    return recordRepository
        .find(tenantId, recordId)
        .orElseThrow(() -> new IllegalArgumentException("work record not found"));
  }

  private WorkRecordTemplateVersion resolveVersion(
      String tenantId, String templateId, String templateVersionId) {
    if (templateVersionId != null && !templateVersionId.isBlank()) {
      return versionRepository
          .find(tenantId, templateVersionId)
          .orElseThrow(() -> new IllegalArgumentException("template version not found"));
    }
    return versionRepository
        .findCurrent(tenantId, templateId)
        .orElseThrow(
            () -> new IllegalArgumentException("published template version not found"));
  }

  private String normalizeObject(String json) {
    try {
      return objectMapper.writeValueAsString(
          objectMapper.readTree(json == null || json.isBlank() ? "{}" : json));
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid json object", ex);
    }
  }

  private void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
  }
}
