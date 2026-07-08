package io.aegisops.workrecord.application;

import io.aegisops.audit.AuditRecordCommand;
import io.aegisops.audit.AuditService;
import io.aegisops.common.json.JsonPayloads;
import io.aegisops.workrecord.api.dto.CreateFieldRequest;
import io.aegisops.workrecord.api.dto.CreateTemplateRequest;
import io.aegisops.workrecord.api.dto.TemplateSchemaRequest;
import io.aegisops.workrecord.api.dto.UpdateFieldRequest;
import io.aegisops.workrecord.api.dto.UpdateTemplateRequest;
import io.aegisops.workrecord.domain.model.FormilyFieldDescriptor;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import io.aegisops.workrecord.domain.rule.WorkRecordFieldValidator;
import io.aegisops.workrecord.infrastructure.persistence.WorkRecordFieldRepository;
import io.aegisops.workrecord.infrastructure.persistence.WorkRecordTemplateRepository;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordTemplateApplicationService {
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
          "switch",
          "boolean");

  private final WorkRecordTemplateRepository templateRepository;
  private final WorkRecordFieldRepository fieldRepository;
  private final WorkRecordSchemaService schemaService;
  private final WorkRecordFieldIndexService fieldIndexService;
  private final AuditService audit;

  public WorkRecordTemplateApplicationService(
      WorkRecordTemplateRepository templateRepository,
      WorkRecordFieldRepository fieldRepository,
      WorkRecordSchemaService schemaService,
      WorkRecordFieldIndexService fieldIndexService,
      AuditService audit) {
    this.templateRepository = templateRepository;
    this.fieldRepository = fieldRepository;
    this.schemaService = schemaService;
    this.fieldIndexService = fieldIndexService;
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
    String safeSchema = schemaService.normalize(request.schemaJson());
    String safeDesigner = schemaService.normalize(request.designerJson());
    WorkRecordTemplate template =
        templateRepository.create(
            tenantId,
            new CreateTemplateRequest(
                request.name(),
                request.code(),
                request.description(),
                request.enabled(),
                safeSchema,
                safeDesigner),
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
        request.schemaJson() == null ? null : schemaService.normalize(request.schemaJson());
    String safeDesigner =
        request.designerJson() == null ? null : schemaService.normalize(request.designerJson());
    UpdateTemplateRequest normalized =
        new UpdateTemplateRequest(
            request.name(), request.description(), request.enabled(), safeSchema, safeDesigner);
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

  /**
   * 保存设计器产出的 Formily schema 并同步字段索引。
   *
   * <p>流程：
   *
   * <ol>
   *   <li>规范化 schema JSON（校验为合法 object）
   *   <li>从 schema 抽取字段描述符列表
   *   <li>同步字段索引（新建/启用/禁用）
   *   <li>保存 schemaJson + designerJson 到模板
   * </ol>
   *
   * @param tenantId 租户 ID
   * @param templateId 模板 ID
   * @param request 包含 schemaJson 和 designerJson 的保存请求
   * @param actor 操作人
   * @return 更新后的模板
   */
  public WorkRecordTemplate saveSchema(
      String tenantId, String templateId, TemplateSchemaRequest request, String actor) {
    requireText(templateId, "templateId");
    if (request == null) {
      throw new IllegalArgumentException("schema request is required");
    }

    // 1. 规范化 schema
    String safeSchema = schemaService.normalize(request.schemaJson());
    String safeDesigner = schemaService.normalize(request.designerJson());

    // 2. 校验保留字段码（在抽取前检查原始 schema，避免 extractFields 过滤后漏检）
    schemaService.validateNoReservedFieldCodes(safeSchema);

    // 3. 从 schema 抽取字段描述符
    List<FormilyFieldDescriptor> descriptors = schemaService.extractFields(safeSchema);

    // 4. 同步字段索引
    List<WorkRecordField> existingFields = fieldRepository.list(tenantId, templateId);
    fieldIndexService.syncFields(tenantId, templateId, descriptors, existingFields, actor);

    // 4. 保存到模板
    WorkRecordTemplate template =
        templateRepository
            .updateSchema(tenantId, templateId, safeSchema, safeDesigner)
            .orElseThrow(() -> new IllegalArgumentException("template not found"));
    audit.record(
        new AuditRecordCommand(
            tenantId,
            defaultActor(actor),
            "work_record.template.schema.update",
            "wr_template",
            template.id(),
            auditDetail(
                "templateId", templateId, "fieldCount", String.valueOf(descriptors.size()))));
    return template;
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
                request.exportable() != null ? request.exportable() : Boolean.TRUE,
                request.statistical(),
                request.sortOrder(),
                request.enabled(),
                request.schemaPath()));
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
            request.exportable(),
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
