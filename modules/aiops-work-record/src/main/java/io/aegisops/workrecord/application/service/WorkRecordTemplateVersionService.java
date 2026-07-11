package io.aegisops.workrecord.application.service;

import io.aegisops.common.exception.AppException;
import io.aegisops.common.exception.ConflictException;
import io.aegisops.common.exception.ResourceNotFoundException;
import io.aegisops.workrecord.application.command.PublishTemplateCommand;
import io.aegisops.workrecord.application.command.TemplateFieldIndexEntry;
import io.aegisops.workrecord.application.command.TemplatePublishValidationResult;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateUsageRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateVersionRepository;
import io.aegisops.workrecord.application.schema.WorkRecordSchemaDocument;
import io.aegisops.workrecord.application.schema.WorkRecordSchemaNormalizer;
import io.aegisops.workrecord.domain.model.TemplateStatus;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import io.aegisops.workrecord.domain.model.WorkRecordTemplateVersion;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkRecordTemplateVersionService {
  private final WorkRecordTemplateRepository templateRepository;
  private final WorkRecordTemplateVersionRepository versionRepository;
  private final WorkRecordFieldIndexRepository fieldRepository;
  private final WorkRecordTemplateUsageRepository usageRepository;
  private final WorkRecordSchemaService schemaService;
  private final WorkRecordSchemaNormalizer schemaNormalizer;
  private final WorkRecordFieldIndexService fieldIndexService;
  private final WorkRecordTemplatePublishGuard publishGuard;
  private final WorkRecordAuditService auditService;
  private final WorkRecordAuditSnapshots auditSnapshots;
  private final WorkRecordFieldAuditService fieldAuditService;
  private final WorkRecordPayloadPolicy payloadPolicy;

  public WorkRecordTemplateVersionService(
      WorkRecordTemplateRepository templateRepository,
      WorkRecordTemplateVersionRepository versionRepository,
      WorkRecordFieldIndexRepository fieldRepository,
      WorkRecordTemplateUsageRepository usageRepository,
      WorkRecordSchemaService schemaService,
      WorkRecordSchemaNormalizer schemaNormalizer,
      WorkRecordFieldIndexService fieldIndexService,
      WorkRecordTemplatePublishGuard publishGuard,
      WorkRecordAuditService auditService,
      WorkRecordAuditSnapshots auditSnapshots,
      WorkRecordFieldAuditService fieldAuditService,
      WorkRecordPayloadPolicy payloadPolicy) {
    this.templateRepository = templateRepository;
    this.versionRepository = versionRepository;
    this.fieldRepository = fieldRepository;
    this.usageRepository = usageRepository;
    this.schemaService = schemaService;
    this.schemaNormalizer = schemaNormalizer;
    this.fieldIndexService = fieldIndexService;
    this.publishGuard = publishGuard;
    this.auditService = auditService;
    this.auditSnapshots = auditSnapshots;
    this.fieldAuditService = fieldAuditService;
    this.payloadPolicy = payloadPolicy;
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

      // 发布前重新校验规范化后 JSON 大小，避免草稿历史遗留超大 payload 通过 DB CHECK 才报错。
      payloadPolicy.requireSchema(document.normalizedSchemaJson());
      payloadPolicy.requireDesigner(document.normalizedDesignerJson());

      List<String> errors = validateFieldLocks(tenantId, template, document);
      if (!errors.isEmpty()) {
        return TemplatePublishValidationResult.failed(errors);
      }

      List<WorkRecordField> previousFields = previousFields(tenantId, template);
      long referenced = currentVersionReferencedCount(tenantId, template);
      boolean currentVersionReferenced = referenced > 0;
      List<TemplateFieldIndexEntry> fieldEntries =
          publishGuard.buildFieldIndexEntries(
              previousFields, document.fields(), currentVersionReferenced);

      String effectiveFieldIndexJson = schemaNormalizer.fieldIndexEntryJson(fieldEntries);
      payloadPolicy.requireFieldIndex(effectiveFieldIndexJson);

      List<String> warnings = new ArrayList<>();
      if (referenced > 0) {
        warnings.add("current template version is referenced by " + referenced + " records");
      }

      return TemplatePublishValidationResult.ok(
          document.schemaVersion(), fieldEntries.size(), referenced, warnings);
    } catch (IllegalArgumentException | AppException ex) {
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
      throw new ConflictException(String.join("; ", errors));
    }

    List<WorkRecordField> previousFields = previousFields(tenantId, template);
    boolean referenced = currentVersionReferencedCount(tenantId, template) > 0;
    List<TemplateFieldIndexEntry> fieldEntries =
        publishGuard.buildFieldIndexEntries(previousFields, document.fields(), referenced);

    // 发布前重新校验规范化后 JSON 大小，避免依赖 DB CHECK 才拒绝写入。
    payloadPolicy.requireSchema(document.normalizedSchemaJson());
    payloadPolicy.requireDesigner(document.normalizedDesignerJson());

    String effectiveFieldIndexJson = schemaNormalizer.fieldIndexEntryJson(fieldEntries);
    payloadPolicy.requireFieldIndex(effectiveFieldIndexJson);

    int versionNo = versionRepository.nextVersionNo(tenantId, template.id());
    WorkRecordTemplateVersion version =
        versionRepository.create(
            tenantId,
            template.id(),
            versionNo,
            command.versionName(),
            document.normalizedSchemaJson(),
            document.normalizedDesignerJson(),
            effectiveFieldIndexJson,
            actor);

    fieldIndexService.createForVersion(tenantId, template.id(), version.id(), fieldEntries);
    templateRepository.updateCurrentVersion(tenantId, template.id(), version.id());

    WorkRecordTemplate updatedTemplate = requireTemplate(tenantId, template.id());

    Map<String, Object> beforeSnapshot = new LinkedHashMap<>();
    beforeSnapshot.put("template", auditSnapshots.template(template));

    Map<String, Object> afterSnapshot = new LinkedHashMap<>();
    afterSnapshot.put("template", auditSnapshots.template(updatedTemplate));
    afterSnapshot.put("version", auditSnapshots.version(version));

    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("previousVersionId", template.currentVersionId());
    attributes.put("currentVersionId", version.id());
    attributes.put("versionNo", version.versionNo());
    attributes.put("schemaVersion", document.schemaVersion());
    attributes.put("fieldCount", fieldEntries.size());

    auditService.recordChange(
        tenantId,
        null,
        template.id(),
        "work_record_template",
        template.id(),
        WorkRecordAuditActions.TEMPLATE_PUBLISH,
        actor,
        beforeSnapshot,
        afterSnapshot,
        attributes);

    List<WorkRecordField> currentFields = fieldRepository.listByVersion(tenantId, version.id());

    fieldAuditService.recordPublishedChanges(
        tenantId,
        template.id(),
        template.currentVersionId(),
        version.id(),
        previousFields,
        currentFields,
        actor);
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
        .orElseThrow(
            () -> new ResourceNotFoundException("template not found: " + templateId));
  }

  private void validateTemplatePublishable(WorkRecordTemplate template) {
    if (!template.enabled()) {
      throw new ConflictException("disabled template cannot be published");
    }
    if (template.status() == TemplateStatus.DISABLED) {
      throw new ConflictException("disabled template cannot be published");
    }
    if (template.status() == TemplateStatus.ARCHIVED) {
      throw new ConflictException("archived template cannot be published");
    }
  }
}
