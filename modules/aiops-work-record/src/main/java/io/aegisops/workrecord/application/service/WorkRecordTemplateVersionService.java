package io.aegisops.workrecord.application.service;

import io.aegisops.workrecord.application.command.PublishTemplateCommand;
import io.aegisops.workrecord.application.port.WorkRecordTemplateRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateVersionRepository;
import io.aegisops.workrecord.domain.model.FormFieldDescriptor;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import io.aegisops.workrecord.domain.model.WorkRecordTemplateVersion;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkRecordTemplateVersionService {
  private final WorkRecordTemplateRepository templateRepository;
  private final WorkRecordTemplateVersionRepository versionRepository;
  private final WorkRecordSchemaService schemaService;
  private final WorkRecordFieldIndexService fieldIndexService;
  private final WorkRecordAuditService auditService;

  public WorkRecordTemplateVersionService(
      WorkRecordTemplateRepository templateRepository,
      WorkRecordTemplateVersionRepository versionRepository,
      WorkRecordSchemaService schemaService,
      WorkRecordFieldIndexService fieldIndexService,
      WorkRecordAuditService auditService) {
    this.templateRepository = templateRepository;
    this.versionRepository = versionRepository;
    this.schemaService = schemaService;
    this.fieldIndexService = fieldIndexService;
    this.auditService = auditService;
  }

  @Transactional
  public WorkRecordTemplateVersion publish(
      String tenantId, PublishTemplateCommand command, String actor) {
    WorkRecordTemplate template =
        templateRepository
            .find(tenantId, command.templateId())
            .orElseThrow(() -> new IllegalArgumentException("template not found"));

    String schema = schemaService.normalizeObject(template.draftSchemaJson(), "schemaJson");
    String designer = schemaService.normalizeObject(template.draftDesignerJson(), "designerJson");

    List<FormFieldDescriptor> descriptors = schemaService.extractFields(schema);
    String fieldIndexJson = schemaService.toFieldIndexJson(descriptors);
    int versionNo = versionRepository.nextVersionNo(tenantId, template.id());

    WorkRecordTemplateVersion version =
        versionRepository.create(
            tenantId,
            template.id(),
            versionNo,
            command.versionName(),
            schema,
            designer,
            fieldIndexJson,
            actor);

    fieldIndexService.createForVersion(tenantId, template.id(), version.id(), descriptors);
    templateRepository.updateCurrentVersion(tenantId, template.id(), version.id());

    auditService.record(
        tenantId,
        null,
        template.id(),
        "work_record_template",
        template.id(),
        "work_record.template.publish",
        actor,
        "{\"versionNo\":" + version.versionNo() + "}");
    return version;
  }
}
