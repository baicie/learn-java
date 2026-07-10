package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.FormFieldDescriptor;
import io.aegisops.workrecord.domain.model.OptionSource;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkRecordTemplatePublishGuardTest {
  private final WorkRecordTemplatePublishGuard guard = new WorkRecordTemplatePublishGuard();

  @Test
  void shouldRejectFieldTypeChangeWhenVersionReferenced() {
    WorkRecordField previous = field("priority", FieldType.SELECT, ".properties.priority");
    FormFieldDescriptor next = descriptor("priority", FieldType.TEXT, ".properties.priority");

    List<String> errors = guard.validateFieldLock(List.of(previous), List.of(next), true);

    assertThat(errors).anyMatch(item -> item.contains("fieldType is locked"));
  }

  @Test
  void shouldRejectFieldCodeChangeOnSameSchemaPathWhenVersionReferenced() {
    WorkRecordField previous = field("priority", FieldType.SELECT, ".properties.priority");
    FormFieldDescriptor next = descriptor("priority2", FieldType.SELECT, ".properties.priority");

    List<String> errors = guard.validateFieldLock(List.of(previous), List.of(next), true);

    assertThat(errors).anyMatch(item -> item.contains("fieldCode is locked"));
  }

  @Test
  void shouldCarryMissingReferencedFieldAsDisabledEntry() {
    WorkRecordField previous = field("priority", FieldType.SELECT, ".properties.priority");

    var entries = guard.buildFieldIndexEntries(List.of(previous), List.of(), true);

    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).fieldCode()).isEqualTo("priority");
    assertThat(entries.get(0).enabled()).isFalse();
  }

  @Test
  void shouldAllowFieldChangesWhenVersionNotReferenced() {
    WorkRecordField previous = field("priority", FieldType.SELECT, ".properties.priority");
    FormFieldDescriptor next = descriptor("priority2", FieldType.TEXT, ".properties.priority");

    List<String> errors = guard.validateFieldLock(List.of(previous), List.of(next), false);

    assertThat(errors).isEmpty();
  }

  private WorkRecordField field(String code, FieldType type, String path) {
    return new WorkRecordField(
        "f-" + code,
        "t1",
        "tpl1",
        "v1",
        code,
        code,
        type,
        false,
        null,
        OptionSource.STATIC,
        null,
        "[]",
        path,
        true,
        true,
        true,
        false,
        1,
        true,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private FormFieldDescriptor descriptor(String code, FieldType type, String path) {
    return new FormFieldDescriptor(
        code, code, type, false, OptionSource.STATIC, null, "[]", path, true, true, true, false, 1);
  }
}
