package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.aegisops.workrecord.application.command.DynamicFilterOperator;
import io.aegisops.workrecord.application.command.RecordDynamicFilter;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateRepository;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.OptionSource;
import io.aegisops.workrecord.domain.model.TemplateStatus;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WorkRecordDynamicFilterPolicyServiceTest {
  private final WorkRecordTemplateRepository templateRepository =
      Mockito.mock(WorkRecordTemplateRepository.class);
  private final WorkRecordFieldIndexRepository fieldRepository =
      Mockito.mock(WorkRecordFieldIndexRepository.class);
  private final WorkRecordDynamicFilterPolicyService service =
      new WorkRecordDynamicFilterPolicyService(templateRepository, fieldRepository);

  @Test
  void shouldRequireTemplateWhenDynamicFiltersExist() {
    assertThatThrownBy(
            () ->
                service.normalize(
                    "t1",
                    null,
                    List.of(RecordDynamicFilter.raw("priority", "eq", "P1"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("templateId is required");
  }

  @Test
  void shouldRejectNonFilterableField() {
    prepare(List.of(field("priority", FieldType.SELECT, false)));

    assertThatThrownBy(
            () ->
                service.normalize(
                    "t1",
                    "tpl1",
                    List.of(RecordDynamicFilter.raw("priority", "eq", "P1"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("field is not filterable");
  }

  @Test
  void shouldRejectInvalidFieldCode() {
    prepare(List.of(field("priority", FieldType.SELECT, true)));

    assertThatThrownBy(
            () ->
                service.normalize(
                    "t1",
                    "tpl1",
                    List.of(RecordDynamicFilter.raw("bad-key", "eq", "x"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid fieldCode");
  }

  @Test
  void shouldRejectUnsupportedOperatorForText() {
    prepare(List.of(field("content", FieldType.TEXT, true)));

    assertThatThrownBy(
            () ->
                service.normalize(
                    "t1",
                    "tpl1",
                    List.of(RecordDynamicFilter.raw("content", "gte", "abc"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("operator gte is not allowed for text");
  }

  @Test
  void shouldNormalizeNumberBetween() {
    prepare(List.of(field("cost", FieldType.NUMBER, true)));

    List<RecordDynamicFilter> result =
        service.normalize(
            "t1",
            "tpl1",
            List.of(
                RecordDynamicFilter.normalized(
                    "cost",
                    DynamicFilterOperator.BETWEEN,
                    FieldType.NUMBER,
                    null,
                    List.of(1, "2.00"))));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).operator()).isEqualTo(DynamicFilterOperator.BETWEEN);
    assertThat(result.get(0).fieldType()).isEqualTo(FieldType.NUMBER);
    assertThat(result.get(0).values()).containsExactly("1", "2");
  }

  @Test
  void shouldNormalizeDatetimeWithOffset() {
    prepare(List.of(field("startedAt", FieldType.DATETIME, true)));

    List<RecordDynamicFilter> result =
        service.normalize(
            "t1",
            "tpl1",
            List.of(RecordDynamicFilter.raw("startedAt", "gte", "2026-01-01T10:00:00+08:00")));

    assertThat(result.get(0).value()).isEqualTo("2026-01-01T10:00+08:00");
  }

  @Test
  void shouldRejectDatetimeWithoutOffset() {
    prepare(List.of(field("startedAt", FieldType.DATETIME, true)));

    assertThatThrownBy(
            () ->
                service.normalize(
                    "t1",
                    "tpl1",
                    List.of(RecordDynamicFilter.raw("startedAt", "gte", "2026-01-01T10:00:00"))))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void shouldNormalizeMultiSelectContainsAll() {
    prepare(List.of(field("tags", FieldType.MULTI_SELECT, true)));

    List<RecordDynamicFilter> result =
        service.normalize(
            "t1",
            "tpl1",
            List.of(
                RecordDynamicFilter.normalized(
                    "tags",
                    DynamicFilterOperator.CONTAINS_ALL,
                    FieldType.MULTI_SELECT,
                    null,
                    List.of("a", "b"))));

    assertThat(result.get(0).fieldType()).isEqualTo(FieldType.MULTI_SELECT);
    assertThat(result.get(0).values()).containsExactly("a", "b");
  }

  @Test
  void shouldAllowExistsWithoutValue() {
    prepare(List.of(field("priority", FieldType.SELECT, true)));

    List<RecordDynamicFilter> result =
        service.normalize(
            "t1",
            "tpl1",
            List.of(RecordDynamicFilter.raw("priority", "exists", null)));

    assertThat(result.get(0).operator()).isEqualTo(DynamicFilterOperator.EXISTS);
    assertThat(result.get(0).value()).isNull();
    assertThat(result.get(0).values()).isEmpty();
  }

  @Test
  void shouldRejectBetweenWithWrongNumberOfValues() {
    prepare(List.of(field("cost", FieldType.NUMBER, true)));

    assertThatThrownBy(
            () ->
                service.normalize(
                    "t1",
                    "tpl1",
                    List.of(
                        RecordDynamicFilter.normalized(
                            "cost",
                            DynamicFilterOperator.BETWEEN,
                            FieldType.NUMBER,
                            null,
                            List.of(1)))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between requires exactly two values");
  }

  @Test
  void shouldRejectTooManyFilters() {
    prepare(List.of(field("name", FieldType.TEXT, true)));

    var tooManyFilters = java.util.stream.IntStream.range(0, 21)
        .mapToObj(i -> RecordDynamicFilter.raw("name", "contains", "x"))
        .toList();

    assertThatThrownBy(
            () -> service.normalize("t1", "tpl1", tooManyFilters))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("too many dynamic filters");
  }

  @Test
  void shouldRejectMultiSelectWithEqOperator() {
    prepare(List.of(field("tags", FieldType.MULTI_SELECT, true)));

    assertThatThrownBy(
            () ->
                service.normalize(
                    "t1",
                    "tpl1",
                    List.of(RecordDynamicFilter.raw("tags", "eq", "a"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("operator eq is not allowed for multi_select");
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

  private WorkRecordField field(String code, FieldType type, boolean filterable) {
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
        filterable,
        true,
        false,
        1,
        true,
        now,
        now);
  }
}
