package io.aegisops.workrecord.application.service;

import io.aegisops.workrecord.application.command.RecordListColumn;
import io.aegisops.workrecord.application.command.RecordListMeta;
import io.aegisops.workrecord.application.command.RecordQuickView;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateRepository;
import io.aegisops.workrecord.domain.model.TemplateStatus;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordListMetaService {
  private final WorkRecordTemplateRepository templateRepository;
  private final WorkRecordFieldIndexRepository fieldRepository;
  private final WorkRecordExportPolicy exportPolicy;

  public WorkRecordListMetaService(
      WorkRecordTemplateRepository templateRepository,
      WorkRecordFieldIndexRepository fieldRepository,
      WorkRecordExportPolicy exportPolicy) {
    this.templateRepository = templateRepository;
    this.fieldRepository = fieldRepository;
    this.exportPolicy = exportPolicy;
  }

  public RecordListMeta meta(String tenantId, String selectedTemplateId) {
    List<WorkRecordTemplate> templates =
        templateRepository.list(tenantId, true).stream()
            .filter(template -> template.status() != TemplateStatus.ARCHIVED)
            .filter(
                template ->
                    template.currentVersionId() != null && !template.currentVersionId().isBlank())
            .toList();

    boolean templateScoped = selectedTemplateId != null && !selectedTemplateId.isBlank();

    List<String> versionIds;
    if (templateScoped) {
      WorkRecordTemplate selected =
          templates.stream()
              .filter(template -> template.id().equals(selectedTemplateId))
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("template not found"));

      versionIds = List.of(selected.currentVersionId());
    } else {
      versionIds = templates.stream().map(WorkRecordTemplate::currentVersionId).distinct().toList();
    }

    List<WorkRecordField> rawFields = fieldRepository.listEnabledByVersions(tenantId, versionIds);

    List<WorkRecordField> fields = deduplicateCompatibleFields(rawFields, templateScoped);

    List<RecordListColumn> columns = new ArrayList<>(builtinColumns());
    columns.addAll(listColumns(fields));

    List<RecordListColumn> exportColumns = new ArrayList<>(builtinColumns());
    exportColumns.addAll(exportColumns(fields));

    List<RecordListColumn> filterFields =
        templateScoped
            ? fields.stream()
                .filter(WorkRecordField::filterable)
                .sorted(fieldComparator())
                .map(this::toDynamicColumn)
                .toList()
            : List.of();

    Set<String> dictCodes = new LinkedHashSet<>();
    for (WorkRecordField field : fields) {
      if (field.dictCode() != null && !field.dictCode().isBlank()) {
        dictCodes.add(field.dictCode());
      }
    }

    return new RecordListMeta(
        templates,
        List.copyOf(columns),
        List.copyOf(exportColumns),
        filterFields,
        dictCodes,
        exportPolicy.maxRows(),
        List.of(
            RecordQuickView.MINE.value(),
            RecordQuickView.ALL.value(),
            RecordQuickView.TODAY.value(),
            RecordQuickView.THIS_WEEK.value(),
            RecordQuickView.THIS_MONTH.value(),
            RecordQuickView.RECENT_WORKDAYS.value()));
  }

  private List<WorkRecordField> deduplicateCompatibleFields(
      List<WorkRecordField> fields, boolean templateScoped) {
    Map<String, List<WorkRecordField>> grouped = new LinkedHashMap<>();

    for (WorkRecordField field : fields) {
      grouped.computeIfAbsent(field.fieldCode(), ignored -> new ArrayList<>()).add(field);
    }

    List<WorkRecordField> result = new ArrayList<>();

    for (List<WorkRecordField> sameCode : grouped.values()) {
      WorkRecordField first = sameCode.getFirst();

      boolean compatible =
          sameCode.stream()
              .allMatch(
                  field ->
                      field.fieldType() == first.fieldType()
                          && field.optionSource() == first.optionSource()
                          && Objects.equals(field.dictCode(), first.dictCode()));

      if (templateScoped || compatible) {
        result.add(sameCode.stream().min(fieldComparator()).orElse(first));
      }
    }

    return result.stream().sorted(fieldComparator()).toList();
  }

  private Comparator<WorkRecordField> fieldComparator() {
    return Comparator.comparingInt(WorkRecordField::sortOrder)
        .thenComparing(WorkRecordField::fieldCode);
  }

  private List<RecordListColumn> builtinColumns() {
    return List.of(
        builtin("title", "标题", "text", true, true, true, 10),
        builtin("status", "状态", "select", true, true, true, 20),
        builtin("templateId", "模板", "text", true, false, true, 30),
        builtin("ownerId", "负责人", "user", true, true, true, 40),
        builtin("creatorId", "创建人", "user", false, true, true, 50),
        builtin("recordTime", "记录时间", "datetime", true, true, true, 60),
        builtin("createdAt", "创建时间", "datetime", false, true, true, 70));
  }

  private RecordListColumn builtin(
      String key,
      String title,
      String type,
      boolean visible,
      boolean sortable,
      boolean exportable,
      int order) {
    return new RecordListColumn(
        key, title, "builtin", null, type, null, null, "[]", visible, sortable, exportable, order);
  }

  private List<RecordListColumn> listColumns(List<WorkRecordField> fields) {
    return fields.stream()
        .filter(WorkRecordField::listVisible)
        .sorted(fieldComparator())
        .map(this::toDynamicColumn)
        .toList();
  }

  private List<RecordListColumn> exportColumns(List<WorkRecordField> fields) {
    return fields.stream()
        .filter(WorkRecordField::exportable)
        .sorted(fieldComparator())
        .map(this::toDynamicColumn)
        .toList();
  }

  private RecordListColumn toDynamicColumn(WorkRecordField field) {
    return new RecordListColumn(
        "custom." + field.fieldCode(),
        field.fieldName(),
        "custom",
        field.fieldCode(),
        field.fieldType().value(),
        field.optionSource().value(),
        field.dictCode(),
        field.optionsJson(),
        field.listVisible(),
        false,
        field.exportable(),
        1000 + field.sortOrder());
  }
}
