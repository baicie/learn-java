package io.aegisops.workrecord.application.command;

import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import java.util.List;
import java.util.Set;

public record RecordListMeta(
    List<WorkRecordTemplate> templates,
    List<RecordListColumn> columns,
    List<RecordListColumn> exportColumns,
    List<RecordListColumn> filterFields,
    Set<String> dictCodes,
    int maxExportRows,
    List<String> quickViews) {}