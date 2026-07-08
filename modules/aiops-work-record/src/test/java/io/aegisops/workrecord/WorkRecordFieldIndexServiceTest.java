package io.aegisops.workrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.audit.AuditRecordCommand;
import io.aegisops.audit.AuditService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class WorkRecordFieldIndexServiceTest {

  private static WorkRecordField field(String code, boolean enabled) {
    return new WorkRecordField(
        "f_" + code,
        "tenant_1",
        "template_1",
        code,
        code,
        "text",
        false,
        null,
        "static",
        null,
        "[]",
        true,
        false,
        true,
        false,
        0,
        enabled,
        ".properties." + code,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  @Test
  void validateNoReservedFieldCodes_shouldRejectReservedCodes() {
    WorkRecordSchemaService schemaService = new WorkRecordSchemaService();

    assertThatThrownBy(
            () ->
                schemaService.validateNoReservedFieldCodes(
                    "{\"properties\":{\"id\":{},\"title\":{}}}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reserved");
  }

  @Test
  void validateNoReservedFieldCodes_shouldAcceptNormalCodes() {
    WorkRecordSchemaService schemaService = new WorkRecordSchemaService();

    assertThatCode(
            () ->
                schemaService.validateNoReservedFieldCodes(
                    "{\"properties\":{\"inspector\":{},\"change_id\":{}}}"))
        .doesNotThrowAnyException();
  }

  @Test
  void syncFields_shouldPersistSchemaPathForNewFields() {
    WorkRecordFieldRepository repository = Mockito.mock(WorkRecordFieldRepository.class);
    AuditService audit = Mockito.mock(AuditService.class);
    WorkRecordFieldIndexService service = new WorkRecordFieldIndexService(repository, audit);

    List<FormilyFieldDescriptor> descriptors =
        List.of(
            new FormilyFieldDescriptor(
                "inspector", "巡检人", "text", "static", null, true, false, false, ".properties.inspector"));

    service.syncFields("tenant_1", "template_1", descriptors, List.of(), "actor-1");

    verify(repository)
        .createInBatch(
            org.mockito.ArgumentMatchers.eq("tenant_1"),
            org.mockito.ArgumentMatchers.eq("template_1"),
            org.mockito.ArgumentMatchers.argThat(
                requests ->
                    requests.size() == 1
                        && requests.getFirst().schemaPath().equals(".properties.inspector")));
  }

  @Test
  void syncFields_createsAuditForEachNewField() {
    WorkRecordFieldRepository repository = Mockito.mock(WorkRecordFieldRepository.class);
    AuditService audit = Mockito.mock(AuditService.class);
    WorkRecordFieldIndexService service = new WorkRecordFieldIndexService(repository, audit);

    List<FormilyFieldDescriptor> descriptors =
        List.of(
            new FormilyFieldDescriptor(
                "inspector", "巡检人", "text", "static", null, true, false, false, ".properties.inspector"),
            new FormilyFieldDescriptor(
                "summary", "摘要", "text", "static", null, true, false, false, ".properties.summary"));

    service.syncFields("tenant_1", "template_1", descriptors, List.of(), "actor-1");

    ArgumentCaptor<AuditRecordCommand> captor = ArgumentCaptor.forClass(AuditRecordCommand.class);
    verify(audit, atLeastOnce()).record(captor.capture());
    List<AuditRecordCommand> calls = captor.getAllValues();
    assertThat(calls)
        .extracting(AuditRecordCommand::action)
        .contains("work_record.field.create");
    assertThat(calls)
        .filteredOn(c -> "work_record.field.create".equals(c.action()))
        .extracting(c -> c.detailJson())
        .anyMatch(d -> d.contains("inspector"))
        .anyMatch(d -> d.contains("summary"));
  }

  @Test
  void syncFields_disablesFieldsNoLongerInSchema_andWritesAudit() {
    WorkRecordFieldRepository repository = Mockito.mock(WorkRecordFieldRepository.class);
    AuditService audit = Mockito.mock(AuditService.class);
    WorkRecordFieldIndexService service = new WorkRecordFieldIndexService(repository, audit);

    WorkRecordField existing = field("summary", true);
    WorkRecordField removed = field("legacy", true);

    List<FormilyFieldDescriptor> descriptors =
        List.of(
            new FormilyFieldDescriptor(
                "summary", "摘要", "text", "static", null, true, false, false, ".properties.summary"));

    service.syncFields(
        "tenant_1", "template_1", descriptors, List.of(existing, removed), "actor-1");

    verify(repository).updateEnabled("tenant_1", "template_1", "f_legacy", false);
    verify(repository, never()).updateEnabled("tenant_1", "template_1", "f_summary", false);

    ArgumentCaptor<AuditRecordCommand> captor = ArgumentCaptor.forClass(AuditRecordCommand.class);
    verify(audit, atLeastOnce()).record(captor.capture());
    assertThat(captor.getAllValues())
        .filteredOn(c -> "work_record.field.disable".equals(c.action()))
        .extracting(c -> c.detailJson())
        .anyMatch(d -> d.contains("legacy"));
  }

  @Test
  void syncFields_reenablesDisabledFieldsInSchema() {
    WorkRecordFieldRepository repository = Mockito.mock(WorkRecordFieldRepository.class);
    AuditService audit = Mockito.mock(AuditService.class);
    Mockito.doNothing().when(repository).updateEnabled(anyString(), anyString(), anyString(), anyBoolean());
    WorkRecordFieldIndexService service = new WorkRecordFieldIndexService(repository, audit);

    WorkRecordField disabled = field("reappear", false);

    List<FormilyFieldDescriptor> descriptors =
        List.of(
            new FormilyFieldDescriptor(
                "reappear",
                "重现字段",
                "text",
                "static",
                null,
                true,
                false,
                false,
                ".properties.reappear"));

    service.syncFields(
        "tenant_1", "template_1", descriptors, List.of(disabled), "actor-1");

    verify(repository).updateEnabled("tenant_1", "template_1", "f_reappear", true);

    ArgumentCaptor<AuditRecordCommand> captor = ArgumentCaptor.forClass(AuditRecordCommand.class);
    verify(audit, atLeastOnce()).record(captor.capture());
    assertThat(captor.getAllValues())
        .filteredOn(c -> "work_record.field.enable".equals(c.action()))
        .extracting(c -> c.detailJson())
        .anyMatch(d -> d.contains("reappear"));
  }
}
