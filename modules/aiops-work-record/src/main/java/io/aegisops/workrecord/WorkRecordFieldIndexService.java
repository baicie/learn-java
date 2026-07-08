package io.aegisops.workrecord;

import io.aegisops.audit.AuditRecordCommand;
import io.aegisops.audit.AuditService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Formily schema 字段索引同步服务。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将 Formily schema 抽取的字段描述符列表同步写入 wr_template_field
 *   <li>以 fieldCode 为幂等键：同 templateId + fieldCode 只保留一条记录
 *   <li>保留字段级别的 enabled、sortOrder 等编辑器状态（由前端传入），不覆盖
 *   <li>被删除的字段在数据库中标记 disabled，不物理删除
 *   <li>为新建 / 禁用字段写审计日志（work_record.field.create / .disable）
 * </ul>
 *
 * <p>与 {@link WorkRecordTemplateService#saveSchema} 配合使用：SchemaService 负责规范化和抽取，
 * FieldIndexService 负责幂等同步字段索引 + 审计。
 */
@Service
public class WorkRecordFieldIndexService {

  private final WorkRecordFieldRepository fieldRepository;
  private final AuditService audit;

  public WorkRecordFieldIndexService(
      WorkRecordFieldRepository fieldRepository, AuditService audit) {
    this.fieldRepository = fieldRepository;
    this.audit = audit;
  }

  /**
   * 将 Formily schema 抽取的字段列表同步到 wr_template_field。
   *
   * <p>对每个新创建 / 重新启用 / 软删除的字段写审计日志，action 取自 {@code
   * work_record.field.create} / {@code work_record.field.disable}。
   *
   * @param tenantId 租户 ID
   * @param templateId 模板 ID
   * @param descriptors 从 schema 抽取的字段描述符列表
   * @param existingFields 当前数据库中已有的字段（用于判断新建/更新/禁用）
   * @param actor 操作人 userId（用于审计 detailJson）
   */
  public void syncFields(
      String tenantId,
      String templateId,
      List<FormilyFieldDescriptor> descriptors,
      List<WorkRecordField> existingFields,
      String actor) {

    // 构建 existing fieldCode -> existing field map
    Map<String, WorkRecordField> existingMap = new HashMap<>();
    for (WorkRecordField f : existingFields) {
      existingMap.put(f.fieldCode(), f);
    }

    Set<String> newFieldCodes = new HashSet<>();
    List<WorkRecordField> toCreate = new ArrayList<>();
    List<WorkRecordField> toEnable = new ArrayList<>();
    List<WorkRecordField> toDisable = new ArrayList<>();

    for (FormilyFieldDescriptor desc : descriptors) {
      newFieldCodes.add(desc.fieldCode());
      // 校验保留字段码（与 WorkRecordFieldValidator 保持一致）
      WorkRecordFieldValidator.validateFieldCodeNotReserved(desc.fieldCode());
      WorkRecordField existing = existingMap.get(desc.fieldCode());

      if (existing == null) {
        // 新字段：CreateFieldRequest（临时对象，不走 repository.create 直接拼装后批量写入）
        toCreate.add(
            new WorkRecordField(
                null, // id 由 repository 生成
                tenantId,
                templateId,
                desc.fieldName(),
                desc.fieldCode(),
                desc.fieldType(),
                false, // required 默认 false
                null, // defaultValue
                desc.optionSource(),
                desc.dictCode(),
                "[]", // optionsJson：静态选项由前端写入 schema，字段索引不重复存储
                desc.listVisible(),
                desc.filterable(),
                true, // exportable 默认 true
                desc.statistical(),
                0, // sortOrder 由前端 schema 中的顺序决定（前端负责传入有序列表）
                true, // enabled
                desc.schemaPath(),
                null, // createdAt
                null)); // updatedAt
      } else if (!existing.enabled()) {
        // 被禁用的字段重新出现：重新启用
        toEnable.add(existing);
      }
      // 已存在且 enabled 的字段不做更新：设计器在 UI 中更新 sortOrder、required 等，
      // 这些变更由前端直接传入 UpdateFieldRequest，不通过 schema 同步路径覆盖。
    }

    // 禁用不再出现在 schema 中的字段（软删除）
    for (WorkRecordField existing : existingMap.values()) {
      if (!newFieldCodes.contains(existing.fieldCode()) && existing.enabled()) {
        toDisable.add(existing);
      }
    }

    // 批量写入新字段（复用 repository 的 create 路径）
    if (!toCreate.isEmpty()) {
      List<CreateFieldRequest> createRequests =
          toCreate.stream()
              .map(
                  f ->
                      new CreateFieldRequest(
                          f.fieldName(),
                          f.fieldCode(),
                          f.fieldType(),
                          f.required(),
                          f.defaultValue(),
                          f.optionSource(),
                          f.dictCode(),
                          f.optionsJson(),
                          f.listVisible(),
                          f.filterable(),
                          f.exportable(),
                          f.statistical(),
                          f.sortOrder(),
                          f.enabled(),
                          f.schemaPath()))
              .toList();
      fieldRepository.createInBatch(tenantId, templateId, createRequests);
      // 审计：每个新字段一条
      for (WorkRecordField created : toCreate) {
        audit.record(
            new AuditRecordCommand(
                tenantId,
                defaultActor(actor),
                "work_record.field.create",
                "wr_template_field",
                created.fieldCode(),
                auditDetail(
                    "templateId",
                    templateId,
                    "fieldCode",
                    created.fieldCode(),
                    "fieldType",
                    created.fieldType(),
                    "source",
                    "schema_sync")));
      }
    }

    // 重新启用之前被禁用的字段
    for (WorkRecordField reEnable : toEnable) {
      fieldRepository.updateEnabled(tenantId, templateId, reEnable.id(), true);
      audit.record(
          new AuditRecordCommand(
              tenantId,
              defaultActor(actor),
              "work_record.field.enable",
              "wr_template_field",
              reEnable.id(),
              auditDetail(
                  "templateId", templateId,
                  "fieldCode", reEnable.fieldCode(),
                  "source", "schema_sync")));
    }

    // 批量禁用废弃字段（软删除）
    for (WorkRecordField toDisableField : toDisable) {
      fieldRepository.updateEnabled(tenantId, templateId, toDisableField.id(), false);
      audit.record(
          new AuditRecordCommand(
              tenantId,
              defaultActor(actor),
              "work_record.field.disable",
              "wr_template_field",
              toDisableField.id(),
              auditDetail(
                  "templateId", templateId,
                  "fieldCode", toDisableField.fieldCode(),
                  "source", "schema_sync")));
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
