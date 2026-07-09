package io.aegisops.workrecord.application.service;

import io.aegisops.workrecord.application.command.CopyTemplateCommand;
import io.aegisops.workrecord.application.command.CreateTemplateCommand;
import io.aegisops.workrecord.application.command.UpdateTemplateCommand;
import io.aegisops.workrecord.application.command.UpdateTemplateDraftCommand;
import io.aegisops.workrecord.application.port.WorkRecordTemplateRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateUsageRepository;
import io.aegisops.workrecord.domain.model.TemplateStatus;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkRecordTemplateService {
  private final WorkRecordTemplateRepository repository;
  private final WorkRecordTemplateUsageRepository usageRepository;
  private final WorkRecordSchemaService schemaService;
  private final WorkRecordAuditService auditService;

  public WorkRecordTemplateService(
      WorkRecordTemplateRepository repository,
      WorkRecordTemplateUsageRepository usageRepository,
      WorkRecordSchemaService schemaService,
      WorkRecordAuditService auditService) {
    this.repository = repository;
    this.usageRepository = usageRepository;
    this.schemaService = schemaService;
    this.auditService = auditService;
  }

  public List<WorkRecordTemplate> list(String tenantId, boolean includeDisabled) {
    return repository.list(tenantId, includeDisabled);
  }

  public WorkRecordTemplate get(String tenantId, String templateId) {
    return repository
        .find(tenantId, templateId)
        .orElseThrow(() -> new IllegalArgumentException("template not found"));
  }

  @Transactional
  public WorkRecordTemplate create(String tenantId, CreateTemplateCommand command, String actor) {
    requireText(command.code(), "code");
    requireText(command.name(), "name");
    ensureCodeAvailable(tenantId, command.code());

    CreateTemplateCommand normalized =
        new CreateTemplateCommand(
            command.code(),
            command.name(),
            command.description(),
            schemaService.normalizeObject(command.draftSchemaJson(), "schemaJson"),
            schemaService.normalizeObject(command.draftDesignerJson(), "designerJson"));

    WorkRecordTemplate template = repository.create(tenantId, normalized, actor);
    auditService.record(
        tenantId,
        null,
        template.id(),
        "work_record_template",
        template.id(),
        "work_record.template.create",
        actor,
        "{\"code\":\"" + escape(template.code()) + "\"}");
    return template;
  }

  @Transactional
  public WorkRecordTemplate update(
      String tenantId, String templateId, UpdateTemplateCommand command, String actor) {
    WorkRecordTemplate template = get(tenantId, templateId);
    ensureEditable(template);

    WorkRecordTemplate updated = repository.update(tenantId, templateId, command);
    auditService.record(
        tenantId,
        null,
        templateId,
        "work_record_template",
        templateId,
        "work_record.template.update",
        actor,
        "{}");
    return updated;
  }

  @Transactional
  public WorkRecordTemplate updateDraft(
      String tenantId, String templateId, UpdateTemplateDraftCommand command, String actor) {
    WorkRecordTemplate template = get(tenantId, templateId);
    ensureEditable(template);

    UpdateTemplateDraftCommand normalized =
        new UpdateTemplateDraftCommand(
            command.name(),
            command.description(),
            command.draftSchemaJson() == null
                ? null
                : schemaService.normalizeObject(command.draftSchemaJson(), "schemaJson"),
            command.draftDesignerJson() == null
                ? null
                : schemaService.normalizeObject(command.draftDesignerJson(), "designerJson"));

    WorkRecordTemplate updated = repository.updateDraft(tenantId, templateId, normalized);
    auditService.record(
        tenantId,
        null,
        templateId,
        "work_record_template",
        templateId,
        "work_record.template.update_draft",
        actor,
        "{}");
    return updated;
  }

  @Transactional
  public WorkRecordTemplate copy(String tenantId, CopyTemplateCommand command, String actor) {
    WorkRecordTemplate source = get(tenantId, command.sourceTemplateId());
    requireText(command.targetCode(), "targetCode");
    requireText(command.targetName(), "targetName");
    ensureCodeAvailable(tenantId, command.targetCode());

    WorkRecordTemplate copied =
        repository.create(
            tenantId,
            new CreateTemplateCommand(
                command.targetCode(),
                command.targetName(),
                command.description() == null ? source.description() : command.description(),
                source.draftSchemaJson(),
                source.draftDesignerJson()),
            actor);

    auditService.record(
        tenantId,
        null,
        copied.id(),
        "work_record_template",
        copied.id(),
        "work_record.template.copy",
        actor,
        "{\"sourceTemplateId\":\"" + escape(source.id()) + "\"}");
    return copied;
  }

  @Transactional
  public WorkRecordTemplate enable(String tenantId, String templateId, String actor) {
    WorkRecordTemplate template = get(tenantId, templateId);
    if (template.status() == TemplateStatus.ARCHIVED) {
      throw new IllegalStateException("archived template cannot be enabled");
    }
    repository.enable(tenantId, templateId);
    auditService.record(
        tenantId,
        null,
        templateId,
        "work_record_template",
        templateId,
        "work_record.template.enable",
        actor,
        "{}");
    return get(tenantId, templateId);
  }

  @Transactional
  public WorkRecordTemplate disable(String tenantId, String templateId, String actor) {
    get(tenantId, templateId);
    repository.disable(tenantId, templateId);
    auditService.record(
        tenantId,
        null,
        templateId,
        "work_record_template",
        templateId,
        "work_record.template.disable",
        actor,
        "{}");
    return get(tenantId, templateId);
  }

  @Transactional
  public WorkRecordTemplate archive(String tenantId, String templateId, String actor) {
    get(tenantId, templateId);
    long references = usageRepository.countRecordsByTemplate(tenantId, templateId);
    if (references > 0) {
      throw new IllegalStateException("template is referenced by records and cannot be archived");
    }
    repository.archive(tenantId, templateId);
    auditService.record(
        tenantId,
        null,
        templateId,
        "work_record_template",
        templateId,
        "work_record.template.archive",
        actor,
        "{}");
    return get(tenantId, templateId);
  }

  private void ensureEditable(WorkRecordTemplate template) {
    if (template.status() == TemplateStatus.ARCHIVED) {
      throw new IllegalStateException("archived template cannot be edited");
    }
    if (!template.enabled() || template.status() == TemplateStatus.DISABLED) {
      throw new IllegalStateException("disabled template cannot be edited");
    }
  }

  private void ensureCodeAvailable(String tenantId, String code) {
    repository
        .findByCode(tenantId, code)
        .ifPresent(
            existing -> {
              throw new IllegalArgumentException("template code already exists: " + code);
            });
  }

  private void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
  }

  private String escape(String value) {
    return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}