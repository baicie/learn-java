package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.aegisops.workrecord.application.command.DynamicFilterOperator;
import io.aegisops.workrecord.application.command.RecordDynamicFilter;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateVersionRepository;
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
  private final WorkRecordTemplateVersionRepository versionRepository =
      Mockito.mock(WorkRecordTemplateVersionRepository.class);
  private final WorkRecordFieldIndexRepository fieldRepository =
      Mockito.mock(WorkRecordFieldIndexRepository.class);
  private final WorkRecordDynamicFilterPolicyService service =
      new WorkRecordDynamicFilterPolicyService(
          templateRepository, versionRepository, fieldRepository);

  // --- P0-1: 严格类型校验 ---

  @Test
  void shouldRejectNumberAsTextForTextField() {
    prepare(List.of(field("content", FieldType.TEXT, true)));

    assertThatThrownBy(
            () ->
                service.normalize(
                    "t1", "tpl1", List.of(RecordDynamicFilter.raw("content", "eq", 123))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("text filter value must be string");
  }

  @Test
  void shouldRejectBooleanAsTextForTextField() {
    prepare(List.of(field("summary", FieldType.TEXT, true)));

    assertThatThrownBy(
            () ->
                service.normalize(
                    "t1", "tpl1", List.of(RecordDynamicFilter.raw("summary", "eq", true))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("text filter value must be string");
  }

  @Test
  void shouldRejectNumberAsTextForSelectField() {
    prepare(List.of(field("priority", FieldType.SELECT, true)));

    assertThatThrownBy(
            () ->
                service.normalize(
                    "t1", "tpl1", List.of(RecordDynamicFilter.raw("priority", "eq", 999))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("text filter value must be string");
  }

  @Test
  void shouldRejectNonStringMultiSelectItems() {
    prepare(List.of(field("tags", FieldType.MULTI_SELECT, true)));

    assertThatThrownBy(
            () ->
                service.normalize(
                    "t1",
                    "tpl1",
                    List.of(
                        RecordDynamicFilter.normalized(
                            "tags",
                            DynamicFilterOperator.CONTAINS_ALL,
                            FieldType.MULTI_SELECT,
                            null,
                            List.of("a", 1)))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("text filter value must be string");
  }

  // --- P0-1: 布尔值允许布尔类型 ---

  @Test
  void shouldAcceptBooleanValueForBooleanField() {
    prepare(List.of(field("urgent", FieldType.BOOLEAN, true)));

    var result =
        service.normalize("t1", "tpl1", List.of(RecordDynamicFilter.raw("urgent", "eq", true)));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).value()).isEqualTo("true");
  }

  @Test
  void shouldAcceptStringBooleanForBooleanField() {
    prepare(List.of(field("urgent", FieldType.BOOLEAN, true)));

    var result =
        service.normalize("t1", "tpl1", List.of(RecordDynamicFilter.raw("urgent", "eq", "false")));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).value()).isEqualTo("false");
  }

  @Test
  void shouldRejectInvalidStringBooleanForBooleanField() {
    prepare(List.of(field("urgent", FieldType.BOOLEAN, true)));

    assertThatThrownBy(
            () ->
                service.normalize(
                    "t1", "tpl1", List.of(RecordDynamicFilter.raw("urgent", "eq", "yes"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("boolean filter value must be boolean");
  }

  // --- P0-1: datetime 必须为 ISO offset ---

  @Test
  void shouldRejectDatetimeWithoutOffset() {
    prepare(List.of(field("startedAt", FieldType.DATETIME, true)));

    assertThatThrownBy(
            () ->
                service.normalize(
                    "t1",
                    "tpl1",
                    List.of(RecordDynamicFilter.raw("startedAt", "gte", "2026-01-01T10:00:00"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("datetime filter value must be ISO offset datetime");
  }

  // --- P1-2: value/values 歧义拒绝 ---

  @Test
  void shouldRejectAmbiguousValueAndValues() {
    prepare(List.of(field("tags", FieldType.MULTI_SELECT, true)));

    RecordDynamicFilter raw =
        new RecordDynamicFilter(
            "tags",
            DynamicFilterOperator.CONTAINS_ANY,
            FieldType.MULTI_SELECT,
            List.of("a"),
            List.of("b"));

    assertThatThrownBy(() -> service.normalize("t1", "tpl1", List.of(raw)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not contain both");
  }

  @Test
  void shouldRejectScalarOperatorWithValues() {
    prepare(List.of(field("content", FieldType.TEXT, true)));

    RecordDynamicFilter raw =
        new RecordDynamicFilter(
            "content", DynamicFilterOperator.EQ, FieldType.TEXT, null, List.of("a"));

    assertThatThrownBy(() -> service.normalize("t1", "tpl1", List.of(raw)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not contain values");
  }

  // --- P1-3: between 上下界顺序 ---

  @Test
  void shouldRejectDescendingNumberBetweenBounds() {
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
                            List.of(100, 10)))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("lower bound must not exceed upper bound");
  }

  @Test
  void shouldRejectDescendingDateBetweenBounds() {
    prepare(List.of(field("recordDate", FieldType.DATE, true)));

    assertThatThrownBy(
            () ->
                service.normalize(
                    "t1",
                    "tpl1",
                    List.of(
                        RecordDynamicFilter.normalized(
                            "recordDate",
                            DynamicFilterOperator.BETWEEN,
                            FieldType.DATE,
                            null,
                            List.of("2026-01-31", "2026-01-01")))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("lower bound must not exceed upper bound");
  }

  @Test
  void shouldAcceptAscendingNumberBetweenBounds() {
    prepare(List.of(field("cost", FieldType.NUMBER, true)));

    var result =
        service.normalize(
            "t1",
            "tpl1",
            List.of(
                RecordDynamicFilter.normalized(
                    "cost",
                    DynamicFilterOperator.BETWEEN,
                    FieldType.NUMBER,
                    null,
                    List.of(10, 100))));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).values()).containsExactly("10", "100");
  }

  // --- P1-3: exists/not_exists 不允许带值 ---

  @Test
  void shouldRejectExistsWithValue() {
    prepare(List.of(field("priority", FieldType.SELECT, true)));

    assertThatThrownBy(
            () ->
                service.normalize(
                    "t1",
                    "tpl1",
                    List.of(RecordDynamicFilter.raw("priority", "exists", "ignored"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not contain value");
  }

  @Test
  void shouldRejectNotExistsWithValues() {
    prepare(List.of(field("priority", FieldType.SELECT, true)));

    assertThatThrownBy(
            () ->
                service.normalize(
                    "t1",
                    "tpl1",
                    List.of(
                        RecordDynamicFilter.normalized(
                            "priority",
                            DynamicFilterOperator.NOT_EXISTS,
                            FieldType.SELECT,
                            null,
                            List.of("x")))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not contain value");
  }

  @Test
  void shouldAllowExistsWithoutValue() {
    prepare(List.of(field("priority", FieldType.SELECT, true)));

    var result =
        service.normalize(
            "t1", "tpl1", List.of(RecordDynamicFilter.raw("priority", "exists", null)));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).operator()).isEqualTo(DynamicFilterOperator.EXISTS);
    assertThat(result.get(0).value()).isNull();
    assertThat(result.get(0).values()).isEmpty();
  }

  // --- 基础校验 ---

  @Test
  void shouldRequireTemplateWhenDynamicFiltersExist() {
    assertThatThrownBy(
            () ->
                service.normalize(
                    "t1", null, List.of(RecordDynamicFilter.raw("priority", "eq", "P1"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("templateId is required");
  }

  @Test
  void shouldRejectNonFilterableField() {
    prepare(List.of(field("priority", FieldType.SELECT, false)));

    assertThatThrownBy(
            () ->
                service.normalize(
                    "t1", "tpl1", List.of(RecordDynamicFilter.raw("priority", "eq", "P1"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("field is not filterable");
  }

  @Test
  void shouldRejectInvalidFieldCode() {
    prepare(List.of(field("priority", FieldType.SELECT, true)));

    assertThatThrownBy(
            () ->
                service.normalize(
                    "t1", "tpl1", List.of(RecordDynamicFilter.raw("bad-key", "eq", "x"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid fieldCode");
  }

  @Test
  void shouldRejectUnsupportedOperatorForText() {
    prepare(List.of(field("content", FieldType.TEXT, true)));

    assertThatThrownBy(
            () ->
                service.normalize(
                    "t1", "tpl1", List.of(RecordDynamicFilter.raw("content", "gte", "abc"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("operator gte is not allowed for text");
  }

  @Test
  void shouldNormalizeNumberBetween() {
    prepare(List.of(field("cost", FieldType.NUMBER, true)));

    var result =
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

    var result =
        service.normalize(
            "t1",
            "tpl1",
            List.of(RecordDynamicFilter.raw("startedAt", "gte", "2026-01-01T10:00:00+08:00")));

    assertThat(result.get(0).value()).isEqualTo("2026-01-01T10:00+08:00");
  }

  @Test
  void shouldNormalizeMultiSelectContainsAll() {
    prepare(List.of(field("tags", FieldType.MULTI_SELECT, true)));

    var result =
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

    var tooManyFilters =
        java.util.stream.IntStream.range(0, 21)
            .mapToObj(i -> RecordDynamicFilter.raw("name", "contains", "x"))
            .toList();

    assertThatThrownBy(() -> service.normalize("t1", "tpl1", tooManyFilters))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("too many dynamic filters");
  }

  @Test
  void shouldRejectMultiSelectWithEqOperator() {
    prepare(List.of(field("tags", FieldType.MULTI_SELECT, true)));

    assertThatThrownBy(
            () ->
                service.normalize(
                    "t1", "tpl1", List.of(RecordDynamicFilter.raw("tags", "eq", "a"))))
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
