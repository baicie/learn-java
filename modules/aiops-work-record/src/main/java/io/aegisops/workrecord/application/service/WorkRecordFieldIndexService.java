package io.aegisops.workrecord.application.service;

import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.domain.model.FormFieldDescriptor;
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
      List<FormFieldDescriptor> descriptors) {
    Set<String> seen = new HashSet<>();
    for (FormFieldDescriptor descriptor : descriptors) {
      FieldCodeRules.validate(descriptor.fieldCode());
      if (!seen.add(descriptor.fieldCode())) {
        throw new IllegalArgumentException("duplicated fieldCode: " + descriptor.fieldCode());
      }
    }
    repository.createForVersion(tenantId, templateId, templateVersionId, descriptors);
  }
}
