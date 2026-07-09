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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordListMetaService {
  private static final int MAX_EXPORT_ROWS = 5000;

  private final WorkRecordTemplateRepository templateRepository;
  private final WorkRecordFieldIndexRepository fieldRepository;

  public WorkRecordListMetaService(
      WorkRecordTemplateRepository templateRepository,
      WorkRecordFieldIndexRepository fieldRepository) {
    this.templateRepository = templateRepository;
    this.fieldRepository = fieldRepository;
  }

  public RecordListMeta meta(String tenantId) {
    List<WorkRecordTemplate> templates =
        templateRepository.list(tenantId, false).stream()
            .filter(item -> item.enabled() && item.status() == TemplateStatus.PUBLISHED)
            .toList();

    List<String> versionIds =
        templates.stream()
            .map(WorkRecordTemplate::currentVersionId)
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .toList();

    List<WorkRecordField> fields = fieldRepository.listEnabledByVersions(tenantId, versionIds);

    List<RecordListColumn> columns = new ArrayList<>(builtinColumns());
    columns.addAll(dynamicColumns(fields));

    List<RecordListColumn> filterFields =
        fields.stream()
            .filter(WorkRecordField::enabled)
            .filter(WorkRecordField::filterable)
            .sorted(
                Comparator.comparingInt(WorkRecordField::sortOrder)
                    .thenComparing(WorkRecordField::fieldCode))
            .map(
                field ->
                    new RecordListColumn(
                        "custom." + field.fieldCode(),
                        field.fieldName(),
                        "custom",
                        field.fieldCode(),
                        field.fieldType().value(),
                        true,
                        false,
                        field.sortOrder()))
            .toList();

    Set<String> dictCodes = new LinkedHashSet<>();
    for (WorkRecordField field : fields) {
      if (field.dictCode() != null && !field.dictCode().isBlank()) {
        dictCodes.add(field.dictCode());
      }
    }

    return new RecordListMeta(
        templates,
        columns,
        filterFields,
        dictCodes,
        MAX_EXPORT_ROWS,
        List.of(
            RecordQuickView.MINE.value(),
            RecordQuickView.ALL.value(),
            RecordQuickView.TODAY.value(),
            RecordQuickView.THIS_WEEK.value(),
            RecordQuickView.THIS_MONTH.value(),
            RecordQuickView.RECENT_WORKDAYS.value()));
  }

  private List<RecordListColumn> builtinColumns() {
    return List.of(
        new RecordListColumn("title", "标题", "builtin", null, "text", true, true, 10),
        new RecordListColumn("status", "状态", "builtin", null, "select", true, true, 20),
        new RecordListColumn("templateId", "模板", "builtin", null, "text", true, false, 30),
        new RecordListColumn("ownerId", "负责人", "builtin", null, "user", true, true, 40),
        new RecordListColumn("creatorId", "创建人", "builtin", null, "user", false, true, 50),
        new RecordListColumn("recordTime", "记录时间", "builtin", null, "datetime", true, true, 60),
        new RecordListColumn("createdAt", "创建时间", "builtin", null, "datetime", false, true, 70));
  }

  private List<RecordListColumn> dynamicColumns(List<WorkRecordField> fields) {
    return fields.stream()
        .filter(WorkRecordField::enabled)
        .filter(WorkRecordField::listVisible)
        .sorted(
            Comparator.comparingInt(WorkRecordField::sortOrder)
                .thenComparing(WorkRecordField::fieldCode))
        .map(
            field ->
                new RecordListColumn(
                    "custom." + field.fieldCode(),
                    field.fieldName(),
                    "custom",
                    field.fieldCode(),
                    field.fieldType().value(),
                    true,
                    false,
                    1000 + field.sortOrder()))
        .toList();
  }
}