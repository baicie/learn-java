package io.aegisops.workrecord.application.service;

import io.aegisops.workrecord.domain.model.WorkRecordField;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordFieldAuditService {
  private final WorkRecordAuditService auditService;
  private final WorkRecordAuditSnapshots snapshots;

  public WorkRecordFieldAuditService(
      WorkRecordAuditService auditService, WorkRecordAuditSnapshots snapshots) {
    this.auditService = auditService;
    this.snapshots = snapshots;
  }

  public void recordPublishedChanges(
      String tenantId,
      String templateId,
      String previousVersionId,
      String currentVersionId,
      List<WorkRecordField> previousFields,
      List<WorkRecordField> currentFields,
      String actorId) {
    Map<String, WorkRecordField> previous = index(previousFields);
    Map<String, WorkRecordField> current = index(currentFields);

    for (WorkRecordField after : current.values()) {
      WorkRecordField before = previous.get(after.fieldCode());

      if (before == null) {
        record(
            tenantId,
            templateId,
            previousVersionId,
            currentVersionId,
            WorkRecordAuditActions.FIELD_CREATE,
            actorId,
            Map.of(),
            snapshots.field(after),
            after.fieldCode());
        continue;
      }

      if (before.enabled() && !after.enabled()) {
        record(
            tenantId,
            templateId,
            previousVersionId,
            currentVersionId,
            WorkRecordAuditActions.FIELD_DISABLE,
            actorId,
            snapshots.field(before),
            snapshots.field(after),
            after.fieldCode());
        continue;
      }

      if (!Objects.equals(snapshots.fieldSemantic(before), snapshots.fieldSemantic(after))) {
        record(
            tenantId,
            templateId,
            previousVersionId,
            currentVersionId,
            WorkRecordAuditActions.FIELD_UPDATE,
            actorId,
            snapshots.field(before),
            snapshots.field(after),
            after.fieldCode());
      }
    }

    // 发布守卫只在旧版本被记录引用时才会把删除字段复制成 enabled=false 留在新版本里。
    // 当旧版本无引用、新版本又不再包含该字段时，必须仍然产生 FIELD_DISABLE 审计，
    // 否则删除操作对审计完全不可见。
    for (WorkRecordField before : previous.values()) {
      if (!before.enabled() || current.containsKey(before.fieldCode())) {
        continue;
      }

      Map<String, Object> after = new LinkedHashMap<>(snapshots.field(before));
      after.put("templateVersionId", currentVersionId);
      after.put("enabled", false);
      after.put("removed", true);

      record(
          tenantId,
          templateId,
          previousVersionId,
          currentVersionId,
          WorkRecordAuditActions.FIELD_DISABLE,
          actorId,
          snapshots.field(before),
          after,
          before.fieldCode());
    }
  }

  private void record(
      String tenantId,
      String templateId,
      String previousVersionId,
      String currentVersionId,
      String action,
      String actorId,
      Object before,
      Object after,
      String fieldCode) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("fieldCode", fieldCode);
    detail.put("previousVersionId", previousVersionId);
    detail.put("currentVersionId", currentVersionId);

    auditService.recordChange(
        tenantId,
        null,
        templateId,
        "work_record_template_field",
        templateId + ":" + fieldCode,
        action,
        actorId,
        before,
        after,
        detail);
  }

  private Map<String, WorkRecordField> index(List<WorkRecordField> fields) {
    if (fields == null) {
      return Map.of();
    }
    return fields.stream()
        .collect(
            Collectors.toMap(
                WorkRecordField::fieldCode,
                Function.identity(),
                (left, right) -> {
                  throw new IllegalStateException("duplicated field code: " + left.fieldCode());
                },
                LinkedHashMap::new));
  }
}
