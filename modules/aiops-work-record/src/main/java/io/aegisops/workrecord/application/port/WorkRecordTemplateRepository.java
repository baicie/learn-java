package io.aegisops.workrecord.application.port;

import io.aegisops.workrecord.application.command.CreateTemplateCommand;
import io.aegisops.workrecord.application.command.UpdateTemplateCommand;
import io.aegisops.workrecord.application.command.UpdateTemplateDraftCommand;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import java.util.List;
import java.util.Optional;

public interface WorkRecordTemplateRepository {
  List<WorkRecordTemplate> list(String tenantId, boolean includeDisabled);

  Optional<WorkRecordTemplate> find(String tenantId, String templateId);

  Optional<WorkRecordTemplate> findByCode(String tenantId, String code);

  WorkRecordTemplate create(String tenantId, CreateTemplateCommand command, String actor);

  WorkRecordTemplate update(String tenantId, String templateId, UpdateTemplateCommand command);

  WorkRecordTemplate updateDraft(
      String tenantId, String templateId, UpdateTemplateDraftCommand command);

  void updateCurrentVersion(String tenantId, String templateId, String versionId);

  void enable(String tenantId, String templateId);

  void disable(String tenantId, String templateId);

  void archive(String tenantId, String templateId);
}