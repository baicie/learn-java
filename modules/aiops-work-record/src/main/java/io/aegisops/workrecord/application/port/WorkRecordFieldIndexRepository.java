package io.aegisops.workrecord.application.port;

import io.aegisops.workrecord.application.command.TemplateFieldIndexEntry;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import java.util.List;

public interface WorkRecordFieldIndexRepository {
  void createForVersion(
      String tenantId,
      String templateId,
      String templateVersionId,
      List<TemplateFieldIndexEntry> fields);

  List<WorkRecordField> listByVersion(String tenantId, String templateVersionId);

  List<WorkRecordField> listByVersions(String tenantId, List<String> templateVersionIds);

  List<WorkRecordField> listEnabledByVersion(String tenantId, String templateVersionId);

  List<WorkRecordField> listEnabledByVersions(String tenantId, List<String> templateVersionIds);

  List<WorkRecordField> listFilterableByVersions(
      String tenantId, List<String> templateVersionIds);
}