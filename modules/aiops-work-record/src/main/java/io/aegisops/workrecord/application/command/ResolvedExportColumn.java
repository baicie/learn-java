package io.aegisops.workrecord.application.command;

import io.aegisops.workrecord.domain.model.WorkRecordField;
import java.util.Collections;
import java.util.Map;

public record ResolvedExportColumn(
    RecordListColumn column, Map<String, WorkRecordField> fieldsByVersion) {

  public ResolvedExportColumn {
    fieldsByVersion =
        fieldsByVersion == null ? Collections.emptyMap() : Map.copyOf(fieldsByVersion);
  }

  public boolean builtin() {
    return "builtin".equals(column.source());
  }

  public String key() {
    return column.key();
  }

  public String title() {
    return column.title();
  }

  public WorkRecordField fieldForVersion(String templateVersionId) {
    if (templateVersionId == null || templateVersionId.isBlank()) {
      return null;
    }
    return fieldsByVersion.get(templateVersionId);
  }
}
