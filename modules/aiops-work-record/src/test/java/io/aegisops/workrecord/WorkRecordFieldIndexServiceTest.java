package io.aegisops.workrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WorkRecordFieldIndexServiceTest {

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

    org.assertj.core.api.Assertions.assertThatCode(
            () ->
                schemaService.validateNoReservedFieldCodes(
                    "{\"properties\":{\"inspector\":{},\"change_id\":{}}}"))
        .doesNotThrowAnyException();
  }

  @Test
  void syncFields_shouldPersistSchemaPathForNewFields() {
    WorkRecordFieldRepository repository = Mockito.mock(WorkRecordFieldRepository.class);
    WorkRecordFieldIndexService service = new WorkRecordFieldIndexService(repository);

    List<FormilyFieldDescriptor> descriptors =
        List.of(
            new FormilyFieldDescriptor(
                "inspector", "巡检人", "text", "static", null, true, false, false, ".properties.inspector"));

    service.syncFields("tenant_1", "template_1", descriptors, List.of());

    verify(repository)
        .createInBatch(
            org.mockito.ArgumentMatchers.eq("tenant_1"),
            org.mockito.ArgumentMatchers.eq("template_1"),
            org.mockito.ArgumentMatchers.argThat(
                requests ->
                    requests.size() == 1
                        && requests.getFirst().schemaPath().equals(".properties.inspector")));
  }
}
