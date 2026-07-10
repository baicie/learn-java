package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.OptionSource;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class WorkRecordFieldAuditServiceTest {

  private final WorkRecordAuditService auditService = Mockito.mock(WorkRecordAuditService.class);
  private final WorkRecordAuditSnapshots snapshots =
      new WorkRecordAuditSnapshots(new ObjectMapper());
  private final WorkRecordFieldAuditService fieldAuditService =
      new WorkRecordFieldAuditService(auditService, snapshots);

  @Test
  void recordPublishedChangesShouldEmitCreateForNewField() {
    WorkRecordField created = field("title", true, true, true);
    fieldAuditService.recordPublishedChanges(
        "t1", "tpl1", "v0", "v1", List.of(), List.of(created), "u1");

    ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
    Mockito.verify(auditService)
        .recordChange(
            Mockito.anyString(),
            Mockito.any(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString(),
            action.capture(),
            Mockito.anyString(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any());
    assertThat(action.getValue()).isEqualTo(WorkRecordAuditActions.FIELD_CREATE);
  }

  @Test
  void recordPublishedChangesShouldEmitDisableForDisabledField() {
    WorkRecordField before = field("title", true, true, true);
    WorkRecordField after = field("title", true, true, false);
    fieldAuditService.recordPublishedChanges(
        "t1", "tpl1", "v0", "v1", List.of(before), List.of(after), "u1");

    ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
    Mockito.verify(auditService)
        .recordChange(
            Mockito.anyString(),
            Mockito.any(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString(),
            action.capture(),
            Mockito.anyString(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any());
    assertThat(action.getValue()).isEqualTo(WorkRecordAuditActions.FIELD_DISABLE);
  }

  @Test
  void recordPublishedChangesShouldEmitUpdateWhenSemanticDiffers() {
    WorkRecordField before = field("title", true, true, true);
    WorkRecordField after =
        new WorkRecordField(
            "f1",
            "t1",
            "tpl1",
            "v1",
            "标题",
            "title",
            FieldType.TEXT,
            true, // required changed false -> true
            null,
            OptionSource.STATIC,
            null,
            "[]",
            null,
            true,
            false,
            true,
            false,
            0,
            true,
            OffsetDateTime.now(),
            OffsetDateTime.now());
    fieldAuditService.recordPublishedChanges(
        "t1", "tpl1", "v0", "v1", List.of(before), List.of(after), "u1");

    ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
    Mockito.verify(auditService)
        .recordChange(
            Mockito.anyString(),
            Mockito.any(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString(),
            action.capture(),
            Mockito.anyString(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any());
    assertThat(action.getValue()).isEqualTo(WorkRecordAuditActions.FIELD_UPDATE);
  }

  @Test
  void recordPublishedChangesShouldNotEmitWhenSemanticIdentical() {
    WorkRecordField before = field("title", true, true, true);
    WorkRecordField after = field("title", true, true, true);
    fieldAuditService.recordPublishedChanges(
        "t1", "tpl1", "v0", "v1", List.of(before), List.of(after), "u1");

    Mockito.verifyNoInteractions(auditService);
  }

  private WorkRecordField field(String code, boolean required, boolean visible, boolean enabled) {
    OffsetDateTime now = OffsetDateTime.now();
    return new WorkRecordField(
        "f-" + code,
        "t1",
        "tpl1",
        "v1",
        code,
        code,
        FieldType.TEXT,
        required,
        null,
        OptionSource.STATIC,
        null,
        "[]",
        null,
        visible,
        false,
        true,
        false,
        0,
        enabled,
        now,
        now);
  }
}
