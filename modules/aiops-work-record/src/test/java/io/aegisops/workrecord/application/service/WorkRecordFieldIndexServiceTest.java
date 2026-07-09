package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.FormFieldDescriptor;
import io.aegisops.workrecord.domain.model.OptionSource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WorkRecordFieldIndexServiceTest {
  private final WorkRecordFieldIndexRepository repository =
      Mockito.mock(WorkRecordFieldIndexRepository.class);
  private final WorkRecordFieldIndexService service = new WorkRecordFieldIndexService(repository);

  @Test
  void shouldRejectDuplicatedFieldCode() {
    var descriptor =
        new FormFieldDescriptor(
            "优先级",
            "priority",
            FieldType.SELECT,
            false,
            OptionSource.DICT,
            "record_priority",
            "[]",
            ".properties.priority",
            true,
            true,
            true,
            false,
            0);

    assertThatThrownBy(
            () -> service.createForVersion("t1", "tpl1", "v1", List.of(descriptor, descriptor)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicated fieldCode");
  }

  @Test
  void shouldDelegateToRepository() {
    var descriptor =
        new FormFieldDescriptor(
            "内容",
            "content",
            FieldType.TEXTAREA,
            true,
            OptionSource.STATIC,
            null,
            "[]",
            ".properties.content",
            true,
            true,
            true,
            false,
            0);

    service.createForVersion("t1", "tpl1", "v1", List.of(descriptor));

    verify(repository).createForVersion("t1", "tpl1", "v1", List.of(descriptor));
  }
}
