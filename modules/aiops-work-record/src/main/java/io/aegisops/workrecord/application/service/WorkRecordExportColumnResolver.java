package io.aegisops.workrecord.application.service;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.RecordListColumn;
import io.aegisops.workrecord.application.command.ResolvedExportColumn;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.domain.model.WorkRecord;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.domain.rule.FieldCodeRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordExportColumnResolver {
  private final WorkRecordFieldIndexRepository fieldRepository;
  private final FieldPolicyService fieldPolicies;

  public WorkRecordExportColumnResolver(
      WorkRecordFieldIndexRepository fieldRepository, FieldPolicyService fieldPolicies) {
    this.fieldRepository = fieldRepository;
    this.fieldPolicies = fieldPolicies;
  }

  public List<ResolvedExportColumn> resolve(
      String tenantId, List<RecordListColumn> requestedColumns, List<WorkRecord> records) {
    return resolve(tenantId, requestedColumns, records, null);
  }

  public List<ResolvedExportColumn> resolve(
      String tenantId,
      List<RecordListColumn> requestedColumns,
      List<WorkRecord> records,
      UserPrincipal principal) {
    if (requestedColumns == null || requestedColumns.isEmpty()) {
      throw new IllegalArgumentException("at least one export column is required");
    }

    Set<String> versionIds = new LinkedHashSet<>();
    for (WorkRecord record : records) {
      if (record.templateVersionId() != null && !record.templateVersionId().isBlank()) {
        versionIds.add(record.templateVersionId());
      }
    }

    List<WorkRecordField> fields =
        fieldRepository.listByVersions(tenantId, List.copyOf(versionIds));

    Map<String, Map<String, WorkRecordField>> fieldsByVersion = indexFields(fields);

    List<ResolvedExportColumn> result = new ArrayList<>();

    for (RecordListColumn candidate : requestedColumns) {
      if (!candidate.exportable()) {
        throw new IllegalArgumentException("column is not exportable: " + candidate.key());
      }

      if ("builtin".equals(candidate.source())) {
        result.add(new ResolvedExportColumn(candidate, Map.of()));
        continue;
      }

      String fieldCode = candidate.fieldCode();
      FieldCodeRules.validate(fieldCode);

      Map<String, WorkRecordField> versionFields = new LinkedHashMap<>();

      for (String versionId : versionIds) {
        Map<String, WorkRecordField> versionMap = fieldsByVersion.getOrDefault(versionId, Map.of());
        WorkRecordField field = versionMap.get(fieldCode);

        if (field == null) {
          continue;
        }

        if (!tenantId.equals(field.tenantId())) {
          throw new IllegalStateException("export field tenant mismatch: " + fieldCode);
        }

        if (!field.exportable()) {
          throw new IllegalArgumentException(
              "field is not exportable in template version " + versionId + ": " + fieldCode);
        }
        if (!fieldPolicies.canReadField(tenantId, versionId, fieldCode, principal)) {
          throw new org.springframework.security.access.AccessDeniedException(
              "not allowed to export field: " + fieldCode);
        }

        versionFields.put(versionId, field);
      }

      result.add(new ResolvedExportColumn(candidate, Map.copyOf(versionFields)));
    }

    return List.copyOf(result);
  }

  private Map<String, Map<String, WorkRecordField>> indexFields(List<WorkRecordField> fields) {
    Map<String, Map<String, WorkRecordField>> result = new HashMap<>();

    for (WorkRecordField field : fields) {
      Map<String, WorkRecordField> versionFields =
          result.computeIfAbsent(field.templateVersionId(), ignored -> new HashMap<>());

      WorkRecordField previous = versionFields.putIfAbsent(field.fieldCode(), field);

      if (previous != null) {
        throw new IllegalStateException(
            "duplicated field metadata: " + field.templateVersionId() + "/" + field.fieldCode());
      }
    }

    return result;
  }
}
