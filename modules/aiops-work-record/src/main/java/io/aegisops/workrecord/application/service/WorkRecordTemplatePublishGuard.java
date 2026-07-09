package io.aegisops.workrecord.application.service;

import io.aegisops.workrecord.application.command.TemplateFieldIndexEntry;
import io.aegisops.workrecord.domain.model.FormFieldDescriptor;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordTemplatePublishGuard {

  public List<String> validateFieldLock(
      List<WorkRecordField> previousFields,
      List<FormFieldDescriptor> nextFields,
      boolean previousVersionReferenced) {
    if (!previousVersionReferenced) {
      return List.of();
    }

    List<String> errors = new ArrayList<>();
    Map<String, WorkRecordField> previousByCode = new HashMap<>();
    Map<String, FormFieldDescriptor> nextByCode = new HashMap<>();
    Map<String, FormFieldDescriptor> nextByPath = new HashMap<>();

    for (WorkRecordField field : previousFields) {
      previousByCode.put(field.fieldCode(), field);
    }
    for (FormFieldDescriptor field : nextFields) {
      nextByCode.put(field.fieldCode(), field);
      if (field.schemaPath() != null) {
        nextByPath.put(field.schemaPath(), field);
      }
    }

    for (WorkRecordField previous : previousFields) {
      FormFieldDescriptor sameCode = nextByCode.get(previous.fieldCode());

      if (sameCode != null) {
        if (previous.fieldType() != sameCode.fieldType()) {
          errors.add(
              "fieldType is locked because records already reference template version: "
                  + previous.fieldCode());
        }

        if (previous.schemaPath() != null
            && !previous.schemaPath().equals(sameCode.schemaPath())) {
          errors.add(
              "schemaPath is locked because records already reference template version: "
                  + previous.fieldCode()
                  + " -> "
                  + sameCode.schemaPath());
        }
      } else if (previous.schemaPath() != null) {
        FormFieldDescriptor samePath = nextByPath.get(previous.schemaPath());
        if (samePath != null && !samePath.fieldCode().equals(previous.fieldCode())) {
          errors.add(
              "fieldCode is locked because records already reference template version: "
                  + previous.fieldCode()
                  + " -> "
                  + samePath.fieldCode());
        }
      }
    }

    return errors;
  }

  public List<TemplateFieldIndexEntry> buildFieldIndexEntries(
      List<WorkRecordField> previousFields,
      List<FormFieldDescriptor> nextFields,
      boolean previousVersionReferenced) {
    List<TemplateFieldIndexEntry> entries = new ArrayList<>();
    for (FormFieldDescriptor next : nextFields) {
      entries.add(TemplateFieldIndexEntry.enabled(next));
    }

    if (previousVersionReferenced) {
      Map<String, FormFieldDescriptor> nextByCode = new HashMap<>();
      for (FormFieldDescriptor next : nextFields) {
        nextByCode.put(next.fieldCode(), next);
      }

      for (WorkRecordField previous : previousFields) {
        if (!nextByCode.containsKey(previous.fieldCode())) {
          entries.add(TemplateFieldIndexEntry.disabledFrom(previous));
        }
      }
    }

    return entries.stream()
        .sorted(
            Comparator.comparingInt(TemplateFieldIndexEntry::sortOrder)
                .thenComparing(TemplateFieldIndexEntry::fieldCode))
        .toList();
  }
}