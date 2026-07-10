package io.aegisops.workrecord.application.service;

import io.aegisops.workrecord.application.command.TemplateFieldIndexEntry;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.domain.rule.FieldCodeRules;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordFieldIndexService {
  private final WorkRecordFieldIndexRepository repository;

  public WorkRecordFieldIndexService(WorkRecordFieldIndexRepository repository) {
    this.repository = repository;
  }

  public void createForVersion(
      String tenantId,
      String templateId,
      String templateVersionId,
      List<TemplateFieldIndexEntry> fields) {
    Set<String> seen = new HashSet<>();
    for (TemplateFieldIndexEntry field : fields) {
      FieldCodeRules.validate(field.fieldCode());
      if (!seen.add(field.fieldCode())) {
        throw new IllegalArgumentException("duplicated fieldCode: " + field.fieldCode());
      }
    }
    repository.createForVersion(tenantId, templateId, templateVersionId, fields);
  }
}
