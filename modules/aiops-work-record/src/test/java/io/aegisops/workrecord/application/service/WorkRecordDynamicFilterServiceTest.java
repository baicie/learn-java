package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.aegisops.workrecord.application.command.RecordDynamicFilter;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateRepository;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.OptionSource;
import io.aegisops.workrecord.domain.model.TemplateStatus;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WorkRecordDynamicFilterServiceTest {
  private final WorkRecordTemplateRepository templateRepository =
      Mockito.mock(WorkRecordTemplateRepository.class);
  private final WorkRecordFieldIndexRepository fieldRepository =
      Mockito.mock(WorkRecordFieldIndexRepository.class);

  private final WorkRecordDynamicFilterService service =
      new WorkRecordDynamicFilterService(templateRepository, fieldRepository);

  @Test
  void shouldRejectFilterWithoutTemplate() {
    assertThatThrownBy(
            () ->
                service.validateAndNormalize(
                    "t1",
                    null,
                    List.of(new RecordDynamicFilter("priority", "eq", "P1"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("templateId is required");
  }

  @Test
  void shouldRejectNonFilterableField() {
    prepare(List.of());

    assertThatThrownBy(
            () ->
                service.validateAndNormalize(
                    "t1",
                    "tpl1",
                    List.of(new RecordDynamicFilter("secret", "eq", "x"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("field is not filterable");
  }

  @Test
  void shouldRejectArrayOperatorForScalarNumber() {
    prepare(List.of(field("cost", FieldType.NUMBER)));

    assertThatThrownBy(
            () ->
                service.validateAndNormalize(
                    "t1",
                    "tpl1",
                    List.of(new RecordDynamicFilter("cost", "in", List.of(1, 2)))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not supported");
  }

  @Test
  void shouldNormalizeMultiSelectInFilter() {
    prepare(List.of(field("tags", FieldType.MULTI_SELECT)));

    var result =
        service.validateAndNormalize(
            "t1",
            "tpl1",
            List.of(new RecordDynamicFilter("tags", "in", List.of("a", "b"))));

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().fieldType()).isEqualTo("multi_select");
    assertThat(result.getFirst().value()).isEqualTo(List.of("a", "b"));
  }

  @Test
  void shouldNormalizeDateRangeFilter() {
    prepare(List.of(field("recordDate", FieldType.DATE)));

    var result =
        service.validateAndNormalize(
            "t1",
            "tpl1",
            List.of(
                new RecordDynamicFilter(
                    "recordDate", "gte", LocalDate.of(2026, 1, 1).toString())));

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().operator()).isEqualTo("gte");
    assertThat(result.getFirst().fieldType()).isEqualTo("date");
  }

  @Test
  void shouldNormalizeContainsFilter() {
    prepare(List.of(field("summary", FieldType.TEXTAREA)));

    var result =
        service.validateAndNormalize(
            "t1",
            "tpl1",
            List.of(new RecordDynamicFilter("summary", "contains", "异常")));

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().operator()).isEqualTo("contains");
  }

  @Test
  void shouldRejectBlankContainsFilter() {
    prepare(List.of(field("summary", FieldType.TEXTAREA)));

    assertThatThrownBy(
            () ->
                service.validateAndNormalize(
                    "t1",
                    "tpl1",
                    List.of(new RecordDynamicFilter("summary", "contains", ""))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blank");
  }

  @Test
  void shouldRejectUnknownOperator() {
    prepare(List.of(field("summary", FieldType.TEXT)));

    assertThatThrownBy(
            () ->
                service.validateAndNormalize(
                    "t1",
                    "tpl1",
                    List.of(new RecordDynamicFilter("summary", "between", "x"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported dynamic filter operator");
  }

  private void prepare(List<WorkRecordField> fields) {
    when(templateRepository.find("t1", "tpl1")).thenReturn(Optional.of(template()));
    when(fieldRepository.listFilterableByVersions("t1", List.of("v1"))).thenReturn(fields);
  }

  private WorkRecordTemplate template() {
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
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        null);
  }

  private WorkRecordField field(String code, FieldType type) {
    OffsetDateTime now = OffsetDateTime.now();
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
        "[\"a\",\"b\"]",
        ".properties." + code,
        true,
        true,
        true,
        false,
        1,
        true,
        now,
        now);
  }
}