package io.aegisops.workrecord.application.port;

import io.aegisops.workrecord.application.command.CreateTemplateCommand;
import io.aegisops.workrecord.application.command.UpdateTemplateDraftCommand;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import java.util.List;
import java.util.Optional;

public interface WorkRecordTemplateRepository {
  List<WorkRecordTemplate> list(String tenantId);

  Optional<WorkRecordTemplate> find(String tenantId, String templateId);

  WorkRecordTemplate create(String tenantId, CreateTemplateCommand command, String actor);

  WorkRecordTemplate updateDraft(
      String tenantId, String templateId, UpdateTemplateDraftCommand command);

  void updateCurrentVersion(String tenantId, String templateId, String versionId);

  void disable(String tenantId, String templateId);
}
