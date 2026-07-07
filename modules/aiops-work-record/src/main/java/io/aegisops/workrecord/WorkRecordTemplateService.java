package io.aegisops.workrecord;

import io.aegisops.audit.AuditRecordCommand;
import io.aegisops.audit.AuditService;
import io.aegisops.common.json.JsonPayloads;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordTemplateService {
  private static final Set<String> FIELD_TYPES =
      Set.of(
          "text",
          "textarea",
          "number",
          "date",
          "datetime",
          "select",
          "multi_select",
          "user",
          "switch");

  private final WorkRecordTemplateRepository templateRepository;
  private final WorkRecordFieldRepository fieldRepository;
  private final AuditService audit;

  public WorkRecordTemplateService(
      WorkRecordTemplateRepository templateRepository,
      WorkRecordFieldRepository fieldRepository,
      AuditService audit) {
    this.templateRepository = templateRepository;
    this.fieldRepository = fieldRepository;
    this.audit = audit;
  }

  public List<WorkRecordTemplate> listTemplates(String tenantId) {
    return templateRepository.list(tenantId);
  }

  public WorkRecordTemplate createTemplate(
      String tenantId, CreateTemplateRequest request, String createdBy) {
    if (request == null) {
      throw new IllegalArgumentException("template request is required");
    }
    requireText(request.name(), "name");
    requireText(request.code(), "code");
    String safeSchema = JsonPayloads.normalizeObject(request.schemaJson(), "schemaJson");
    WorkRecordTemplate template =
        templateRepository.create(
            tenantId,
            new CreateTemplateRequest(
                request.name(),
                request.code(),
                request.description(),
                request.enabled(),
                safeSchema),
            defaultActor(createdBy));
    audit.record(
        new AuditRecordCommand(
            tenantId,
            defaultActor(createdBy),
            "work_record.template.create",
            "wr_template",
            template.id(),
            auditDetail("code", template.code(), "name", template.name())));
    return template;
  }

  public WorkRecordTemplate updateTemplate(
      String tenantId, String templateId, UpdateTemplateRequest request, String actor) {
    requireText(templateId, "templateId");
    if (request == null) {
      throw new IllegalArgumentException("update request is required");
    }
    String safeSchema =
        request.schemaJson() == null
            ? null
            : JsonPayloads.normalizeObject(request.schemaJson(), "schemaJson");
    UpdateTemplateRequest normalized =
        new UpdateTemplateRequest(
            request.name(), request.description(), request.enabled(), safeSchema);
    WorkRecordTemplate updated =
        templateRepository
            .update(tenantId, templateId, normalized)
            .orElseThrow(() -> new IllegalArgumentException("template not found"));
    audit.record(
        new AuditRecordCommand(
            tenantId,
            defaultActor(actor),
            "work_record.template.update",
            "wr_template",
            updated.id(),
            auditDetail("code", updated.code(), "enabled", String.valueOf(updated.enabled()))));
    return updated;
  }

  public List<WorkRecordField> listFields(String tenantId, String templateId) {
    requireText(templateId, "templateId");
    return fieldRepository.list(tenantId, templateId);
  }

  public WorkRecordField createField(
      String tenantId, String templateId, CreateFieldRequest request, String actor) {
    if (request == null) {
      throw new IllegalArgumentException("field request is required");
    }
    requireText(templateId, "templateId");
    requireText(request.fieldName(), "fieldName");
    requireText(request.fieldCode(), "fieldCode");
    WorkRecordFieldValidator.validateFieldCodeNotReserved(request.fieldCode());
    requireText(request.fieldType(), "fieldType");
    if (!FIELD_TYPES.contains(request.fieldType())) {
      throw new IllegalArgumentException("unsupported fieldType: " + request.fieldType());
    }
    String optionSource = request.optionSource() == null ? "static" : request.optionSource();
    if ("dict".equals(optionSource)) {
      requireText(request.dictCode(), "dictCode");
    }
    String safeOptions = JsonPayloads.normalizeArray(request.optionsJson(), "optionsJson");
    WorkRecordField field =
        fieldRepository.create(
            tenantId,
            templateId,
            new CreateFieldRequest(
                request.fieldName(),
                request.fieldCode(),
                request.fieldType(),
                request.required(),
                request.defaultValue(),
                optionSource,
                request.dictCode(),
                safeOptions,
                request.listVisible(),
                request.filterable(),
                request.statistical(),
                request.sortOrder(),
                request.enabled()));
    audit.record(
        new AuditRecordCommand(
            tenantId,
            defaultActor(actor),
            "work_record.field.create",
            "wr_template_field",
            field.id(),
            auditDetail(
                "templateId",
                templateId,
                "fieldCode",
                field.fieldCode(),
                "fieldType",
                field.fieldType())));
    return field;
  }

  public WorkRecordField updateField(
      String tenantId,
      String templateId,
      String fieldId,
      UpdateFieldRequest request,
      String actor) {
    requireText(templateId, "templateId");
    requireText(fieldId, "fieldId");
    if (request == null) {
      throw new IllegalArgumentException("update request is required");
    }
    String safeOptions =
        request.optionsJson() == null
            ? null
            : JsonPayloads.normalizeArray(request.optionsJson(), "optionsJson");
    UpdateFieldRequest normalized =
        new UpdateFieldRequest(
            request.fieldName(),
            request.required(),
            request.defaultValue(),
            request.optionSource(),
            request.dictCode(),
            safeOptions,
            request.listVisible(),
            request.filterable(),
            request.statistical(),
            request.sortOrder(),
            request.enabled());
    WorkRecordField updated =
        fieldRepository
            .update(tenantId, templateId, fieldId, normalized)
            .orElseThrow(() -> new IllegalArgumentException("field not found"));
    audit.record(
        new AuditRecordCommand(
            tenantId,
            defaultActor(actor),
            "work_record.field.update",
            "wr_template_field",
            updated.id(),
            auditDetail(
                "templateId",
                templateId,
                "fieldCode",
                updated.fieldCode(),
                "enabled",
                String.valueOf(updated.enabled()))));
    return updated;
  }

  private void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
  }

  private String defaultActor(String userId) {
    return userId == null || userId.isBlank() ? "system" : userId;
  }

  private String auditDetail(String... keyValues) {
    StringBuilder sb = new StringBuilder("{");
    for (int i = 0; i + 1 < keyValues.length; i += 2) {
      if (i > 0) sb.append(',');
      sb.append('"').append(escape(keyValues[i])).append('"');
      sb.append(':');
      sb.append('"').append(escape(keyValues[i + 1] == null ? "" : keyValues[i + 1])).append('"');
    }
    sb.append('}');
    return sb.toString();
  }

  private String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
