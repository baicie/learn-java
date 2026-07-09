package io.aegisops.workrecord.application.service;

import io.aegisops.workrecord.application.command.CreateTemplateCommand;
import io.aegisops.workrecord.application.command.UpdateTemplateDraftCommand;
import io.aegisops.workrecord.application.port.WorkRecordTemplateRepository;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkRecordTemplateService {
  private final WorkRecordTemplateRepository repository;
  private final WorkRecordSchemaService schemaService;
  private final WorkRecordAuditService auditService;

  public WorkRecordTemplateService(
      WorkRecordTemplateRepository repository,
      WorkRecordSchemaService schemaService,
      WorkRecordAuditService auditService) {
    this.repository = repository;
    this.schemaService = schemaService;
    this.auditService = auditService;
  }

  public List<WorkRecordTemplate> list(String tenantId) {
    return repository.list(tenantId);
  }

  public Optional<WorkRecordTemplate> find(String tenantId, String templateId) {
    return repository.find(tenantId, templateId);
  }

  @Transactional
  public WorkRecordTemplate create(String tenantId, CreateTemplateCommand command, String actor) {
    requireText(command.code(), "code");
    requireText(command.name(), "name");

    CreateTemplateCommand normalized =
        new CreateTemplateCommand(
            command.code(),
            command.name(),
            command.description(),
            schemaService.normalizeObject(command.draftSchemaJson(), "draftSchemaJson"),
            schemaService.normalizeObject(command.draftDesignerJson(), "draftDesignerJson"));

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
  public WorkRecordTemplate updateDraft(
      String tenantId, String templateId, UpdateTemplateDraftCommand command, String actor) {
    requireText(templateId, "templateId");
    UpdateTemplateDraftCommand normalized =
        new UpdateTemplateDraftCommand(
            command.name(),
            command.description(),
            command.draftSchemaJson() == null
                ? null
                : schemaService.normalizeObject(command.draftSchemaJson(), "draftSchemaJson"),
            command.draftDesignerJson() == null
                ? null
                : schemaService.normalizeObject(command.draftDesignerJson(), "draftDesignerJson"));

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
  public void disable(String tenantId, String templateId, String actor) {
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
