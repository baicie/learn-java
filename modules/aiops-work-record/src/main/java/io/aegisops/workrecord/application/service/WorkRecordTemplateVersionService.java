package io.aegisops.workrecord.application.service;

import io.aegisops.workrecord.application.command.PublishTemplateCommand;
import io.aegisops.workrecord.application.command.TemplateFieldIndexEntry;
import io.aegisops.workrecord.application.command.TemplatePublishValidationResult;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateUsageRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateVersionRepository;
import io.aegisops.workrecord.application.schema.WorkRecordSchemaDocument;
import io.aegisops.workrecord.domain.model.TemplateStatus;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import io.aegisops.workrecord.domain.model.WorkRecordTemplateVersion;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkRecordTemplateVersionService {
  private final WorkRecordTemplateRepository templateRepository;
  private final WorkRecordTemplateVersionRepository versionRepository;
  private final WorkRecordFieldIndexRepository fieldRepository;
  private final WorkRecordTemplateUsageRepository usageRepository;
  private final WorkRecordSchemaService schemaService;
  private final WorkRecordFieldIndexService fieldIndexService;
  private final WorkRecordTemplatePublishGuard publishGuard;
  private final WorkRecordAuditService auditService;

  public WorkRecordTemplateVersionService(
      WorkRecordTemplateRepository templateRepository,
      WorkRecordTemplateVersionRepository versionRepository,
      WorkRecordFieldIndexRepository fieldRepository,
      WorkRecordTemplateUsageRepository usageRepository,
      WorkRecordSchemaService schemaService,
      WorkRecordFieldIndexService fieldIndexService,
      WorkRecordTemplatePublishGuard publishGuard,
      WorkRecordAuditService auditService) {
    this.templateRepository = templateRepository;
    this.versionRepository = versionRepository;
    this.fieldRepository = fieldRepository;
    this.usageRepository = usageRepository;
    this.schemaService = schemaService;
    this.fieldIndexService = fieldIndexService;
    this.publishGuard = publishGuard;
    this.auditService = auditService;
  }

  public List<WorkRecordTemplateVersion> list(String tenantId, String templateId) {
    requireTemplate(tenantId, templateId);
    return versionRepository.list(tenantId, templateId);
  }

  public WorkRecordTemplateVersion get(String tenantId, String templateId, String versionId) {
    requireTemplate(tenantId, templateId);
    return versionRepository
        .findByTemplateAndVersion(tenantId, templateId, versionId)
        .orElseThrow(() -> new IllegalArgumentException("template version not found"));
  }

  public List<WorkRecordField> fields(String tenantId, String templateId, String versionId) {
    get(tenantId, templateId, versionId);
    return fieldRepository.listByVersion(tenantId, versionId);
  }

  public TemplatePublishValidationResult validatePublish(String tenantId, String templateId) {
    try {
      WorkRecordTemplate template = requireTemplate(tenantId, templateId);
      validateTemplatePublishable(template);

      WorkRecordSchemaDocument document =
          schemaService.prepareForPublish(template.draftSchemaJson(), template.draftDesignerJson());

      List<String> errors = validateFieldLocks(tenantId, template, document);
      if (!errors.isEmpty()) {
        return TemplatePublishValidationResult.failed(errors);
      }

      long referenced = currentVersionReferencedCount(tenantId, template);
      List<String> warnings = new ArrayList<>();
      if (referenced > 0) {
        warnings.add("current template version is referenced by " + referenced + " records");
      }
      return TemplatePublishValidationResult.ok(
          document.schemaVersion(), document.fields().size(), referenced, warnings);
    } catch (IllegalArgumentException | IllegalStateException ex) {
      return TemplatePublishValidationResult.failed(List.of(ex.getMessage()));
    }
  }

  @Transactional
  public WorkRecordTemplateVersion publish(
      String tenantId, PublishTemplateCommand command, String actor) {
    WorkRecordTemplate template = requireTemplate(tenantId, command.templateId());
    validateTemplatePublishable(template);

    WorkRecordSchemaDocument document =
        schemaService.prepareForPublish(template.draftSchemaJson(), template.draftDesignerJson());

    List<String> errors = validateFieldLocks(tenantId, template, document);
    if (!errors.isEmpty()) {
      throw new IllegalStateException(String.join("; ", errors));
    }

    List<WorkRecordField> previousFields = previousFields(tenantId, template);
    boolean referenced = currentVersionReferencedCount(tenantId, template) > 0;
    List<TemplateFieldIndexEntry> fieldEntries =
        publishGuard.buildFieldIndexEntries(previousFields, document.fields(), referenced);

    int versionNo = versionRepository.nextVersionNo(tenantId, template.id());
    WorkRecordTemplateVersion version =
        versionRepository.create(
            tenantId,
            template.id(),
            versionNo,
            command.versionName(),
            document.normalizedSchemaJson(),
            document.normalizedDesignerJson(),
            document.fieldIndexJson(),
            actor);

    fieldIndexService.createForVersion(tenantId, template.id(), version.id(), fieldEntries);
    templateRepository.updateCurrentVersion(tenantId, template.id(), version.id());

    auditService.record(
        tenantId,
        null,
        template.id(),
        "work_record_template",
        template.id(),
        "work_record.template.publish",
        actor,
        "{\"versionNo\":"
            + version.versionNo()
            + ",\"schemaVersion\":"
            + document.schemaVersion()
            + ",\"fieldCount\":"
            + fieldEntries.size()
            + "}");
    return version;
  }

  private List<String> validateFieldLocks(
      String tenantId, WorkRecordTemplate template, WorkRecordSchemaDocument document) {
    List<WorkRecordField> previousFields = previousFields(tenantId, template);
    boolean referenced = currentVersionReferencedCount(tenantId, template) > 0;
    return publishGuard.validateFieldLock(previousFields, document.fields(), referenced);
  }

  private List<WorkRecordField> previousFields(String tenantId, WorkRecordTemplate template) {
    if (template.currentVersionId() == null || template.currentVersionId().isBlank()) {
      return List.of();
    }
    return fieldRepository.listByVersion(tenantId, template.currentVersionId());
  }

  private long currentVersionReferencedCount(String tenantId, WorkRecordTemplate template) {
    if (template.currentVersionId() == null || template.currentVersionId().isBlank()) {
      return 0L;
    }
    return usageRepository.countRecordsByTemplateVersion(tenantId, template.currentVersionId());
  }

  private WorkRecordTemplate requireTemplate(String tenantId, String templateId) {
    return templateRepository
        .find(tenantId, templateId)
        .orElseThrow(() -> new IllegalArgumentException("template not found"));
  }

  private void validateTemplatePublishable(WorkRecordTemplate template) {
    if (!template.enabled()) {
      throw new IllegalStateException("disabled template cannot be published");
    }
    if (template.status() == TemplateStatus.DISABLED) {
      throw new IllegalStateException("disabled template cannot be published");
    }
    if (template.status() == TemplateStatus.ARCHIVED) {
      throw new IllegalStateException("archived template cannot be published");
    }
  }
}