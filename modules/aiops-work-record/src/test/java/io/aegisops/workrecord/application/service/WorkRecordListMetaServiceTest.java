package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateRepository;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.OptionSource;
import io.aegisops.workrecord.domain.model.TemplateStatus;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WorkRecordListMetaServiceTest {
  private final WorkRecordTemplateRepository templateRepository =
      Mockito.mock(WorkRecordTemplateRepository.class);
  private final WorkRecordFieldIndexRepository fieldRepository =
      Mockito.mock(WorkRecordFieldIndexRepository.class);
  private final WorkRecordListMetaService service =
      new WorkRecordListMetaService(templateRepository, fieldRepository);

  @Test
  void shouldBuildMetaWithDynamicColumnsAndFilterFields() {
    when(templateRepository.list("t1", false)).thenReturn(List.of(template()));
    when(fieldRepository.listEnabledByVersions("t1", List.of("v1")))
        .thenReturn(List.of(field("priority", true, true, "record_priority")));

    var meta = service.meta("t1");

    assertThat(meta.templates()).hasSize(1);
    assertThat(meta.columns()).anyMatch(column -> column.key().equals("custom.priority"));
    assertThat(meta.filterFields()).anyMatch(column -> column.fieldCode().equals("priority"));
    assertThat(meta.dictCodes()).contains("record_priority");
    assertThat(meta.quickViews())
        .contains("mine", "all", "today", "this_week", "this_month", "recent_workdays");
  }

  private WorkRecordTemplate template() {
    OffsetDateTime now = OffsetDateTime.now();
    return new WorkRecordTemplate(
        "tpl1",
        "t1",
        "daily",
        "日报",
        null,
        TemplateStatus.PUBLISHED,
        true,
        "v1",
        "{}",
        "{}",
        "u1",
        now,
        now,
        null);
  }

  private WorkRecordField field(
      String code, boolean listVisible, boolean filterable, String dictCode) {
    OffsetDateTime now = OffsetDateTime.now();
    return new WorkRecordField(
        "f-" + code,
        "t1",
        "tpl1",
        "v1",
        code,
        code,
        FieldType.SELECT,
        false,
        null,
        OptionSource.DICT,
        dictCode,
        "[]",
        ".properties." + code,
        listVisible,
        filterable,
        true,
        false,
        1,
        true,
        now,
        now);
  }
}