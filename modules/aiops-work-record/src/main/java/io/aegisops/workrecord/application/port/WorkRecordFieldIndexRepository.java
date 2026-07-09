package io.aegisops.workrecord.application.port;

import io.aegisops.workrecord.domain.model.FormFieldDescriptor;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import java.util.List;

public interface WorkRecordFieldIndexRepository {
  void createForVersion(
      String tenantId,
      String templateId,
      String templateVersionId,
      List<FormFieldDescriptor> descriptors);

  List<WorkRecordField> listByVersion(String tenantId, String templateVersionId);

  List<WorkRecordField> listEnabledByVersion(String tenantId, String templateVersionId);
}
