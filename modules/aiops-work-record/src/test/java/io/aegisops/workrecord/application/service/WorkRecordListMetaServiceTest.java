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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WorkRecordListMetaServiceTest {
  private final WorkRecordTemplateRepository templateRepository =
      Mockito.mock(WorkRecordTemplateRepository.class);
  private final WorkRecordFieldIndexRepository fieldRepository =
      Mockito.mock(WorkRecordFieldIndexRepository.class);
  private final WorkRecordExportPolicy exportPolicy =
      Mockito.mock(WorkRecordExportPolicy.class);

  private final WorkRecordListMetaService service =
      new WorkRecordListMetaService(templateRepository, fieldRepository, exportPolicy);

  @BeforeEach
  void setUp() {
    when(exportPolicy.maxRows()).thenReturn(5000);
  }

  @Test
  void shouldBuildMetaWithDynamicColumnsAndFilterFields() {
    when(templateRepository.list("t1", true)).thenReturn(List.of(template("tpl1", "v1", true)));
    when(fieldRepository.listEnabledByVersions("t1", List.of("v1")))
        .thenReturn(List.of(field("tpl1", "v1", "priority", true, true, FieldType.SELECT)));

    var meta = service.meta("t1", "tpl1");

    assertThat(meta.templates()).hasSize(1);
    assertThat(meta.columns()).anyMatch(column -> column.key().equals("custom.priority"));
    assertThat(meta.filterFields()).anyMatch(column -> column.fieldCode().equals("priority"));
    assertThat(meta.dictCodes()).contains("record_priority");
    assertThat(meta.quickViews())
        .contains("mine", "all", "today", "this_week", "this_month", "recent_workdays");
  }

  @Test
  void shouldDeduplicateCompatibleColumnsAcrossTemplates() {
    when(templateRepository.list("t1", true))
        .thenReturn(
            List.of(template("tpl1", "v1", true), template("tpl2", "v2", true)));

    when(fieldRepository.listEnabledByVersions("t1", List.of("v1", "v2")))
        .thenReturn(
            List.of(
                field("tpl1", "v1", "priority", true, true, FieldType.SELECT),
                field("tpl2", "v2", "priority", true, true, FieldType.SELECT)));

    var meta = service.meta("t1", null);

    assertThat(
            meta.columns().stream()
                .filter(column -> "custom.priority".equals(column.key())))
        .hasSize(1);
  }

  @Test
  void shouldIncludeDisabledTemplateForHistoricalFiltering() {
    when(templateRepository.list("t1", true))
        .thenReturn(List.of(template("tpl1", "v1", false)));

    when(fieldRepository.listEnabledByVersions("t1", List.of("v1")))
        .thenReturn(List.of());

    var meta = service.meta("t1", null);

    assertThat(meta.templates()).extracting(WorkRecordTemplate::id).contains("tpl1");
  }

  @Test
  void shouldProvideEmptyFilterFieldsWhenTemplateNotScoped() {
    when(templateRepository.list("t1", true))
        .thenReturn(List.of(template("tpl1", "v1", true)));

    when(fieldRepository.listEnabledByVersions("t1", List.of("v1")))
        .thenReturn(
            List.of(field("tpl1", "v1", "priority", true, true, FieldType.SELECT)));

    var meta = service.meta("t1", null);

    assertThat(meta.filterFields()).isEmpty();
  }

  private WorkRecordTemplate template(String id, String versionId, boolean enabled) {
    OffsetDateTime now = OffsetDateTime.now();
    return new WorkRecordTemplate(
        id,
        "t1",
        id + "-code",
        id,
        null,
        enabled ? TemplateStatus.PUBLISHED : TemplateStatus.DISABLED,
        enabled,
        versionId,
        "{}",
        "{}",
        "u1",
        now,
        now,
        null);
  }

  private WorkRecordField field(
      String templateId,
      String versionId,
      String code,
      boolean listVisible,
      boolean filterable,
      FieldType type) {
    OffsetDateTime now = OffsetDateTime.now();
    return new WorkRecordField(
        "f-" + code,
        "t1",
        templateId,
        versionId,
        code,
        code,
        type,
        false,
        null,
        OptionSource.DICT,
        "record_priority",
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