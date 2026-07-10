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

/**
 * Phase 11 验收测试：覆盖当前已实现的动态筛选白名单行为。
 *
 * <p>已由 {@link WorkRecordDynamicFilterPolicyServiceTest} 覆盖更多 Phase 11 新操作符场景，
 * 本测试保留以确保 Phase 10 已有行为不退化。
 */
class WorkRecordDynamicFilterServiceTest {
  private final WorkRecordTemplateRepository templateRepository =
      Mockito.mock(WorkRecordTemplateRepository.class);
  private final WorkRecordTemplateVersionRepository versionRepository =
      Mockito.mock(WorkRecordTemplateVersionRepository.class);
  private final WorkRecordFieldIndexRepository fieldRepository =
      Mockito.mock(WorkRecordFieldIndexRepository.class);
  private final WorkRecordDynamicFilterPolicyService service =
      new WorkRecordDynamicFilterPolicyService(templateRepository, versionRepository, fieldRepository);

  @Test
  void shouldRejectFilterWithoutTemplate() {
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
  void shouldRejectArrayOperatorForScalarNumber() {
    prepare(List.of(field("cost", FieldType.NUMBER, true)));

    assertThatThrownBy(
            () ->
                service.normalize(
                    "t1",
                    "tpl1",
                    List.of(
                        RecordDynamicFilter.normalized(
                            "cost",
                            DynamicFilterOperator.IN,
                            FieldType.NUMBER,
                            null,
                            List.of(1, 2)))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("operator in is not allowed for number");
  }

  @Test
  void shouldNormalizeMultiSelectInFilter() {
    prepare(List.of(field("tags", FieldType.MULTI_SELECT, true)));

    List<RecordDynamicFilter> result =
        service.normalize(
            "t1",
            "tpl1",
            List.of(
                RecordDynamicFilter.normalized(
                    "tags",
                    DynamicFilterOperator.IN,
                    FieldType.MULTI_SELECT,
                    null,
                    List.of("a", "b"))));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).fieldType()).isEqualTo(FieldType.MULTI_SELECT);
    assertThat(result.get(0).values()).containsExactly("a", "b");
  }

  @Test
  void shouldNormalizeDateRangeFilter() {
    prepare(List.of(field("recordDate", FieldType.DATE, true)));

    List<RecordDynamicFilter> result =
        service.normalize(
            "t1",
            "tpl1",
            List.of(
                RecordDynamicFilter.raw("recordDate", "gte", "2026-01-01")));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).operator()).isEqualTo(DynamicFilterOperator.GTE);
    assertThat(result.get(0).fieldType()).isEqualTo(FieldType.DATE);
  }

  @Test
  void shouldNormalizeContainsFilter() {
    prepare(List.of(field("summary", FieldType.TEXTAREA, true)));

    List<RecordDynamicFilter> result =
        service.normalize(
            "t1",
            "tpl1",
            List.of(RecordDynamicFilter.raw("summary", "contains", "异常")));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).operator()).isEqualTo(DynamicFilterOperator.CONTAINS);
  }

  @Test
  void shouldRejectBlankContainsFilter() {
    prepare(List.of(field("summary", FieldType.TEXTAREA, true)));

    assertThatThrownBy(
            () ->
                service.normalize(
                    "t1",
                    "tpl1",
                    List.of(RecordDynamicFilter.raw("summary", "contains", ""))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blank");
  }

  @Test
  void shouldRejectUnknownOperator() {
    prepare(List.of(field("summary", FieldType.TEXT, true)));

    assertThatThrownBy(
            () ->
                service.normalize(
                    "t1",
                    "tpl1",
                    List.of(RecordDynamicFilter.raw("summary", "between", "x"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("operator between is not allowed for text");
  }

  @Test
  void shouldRejectNumberAsTextForTextField() {
    prepare(List.of(field("content", FieldType.TEXT, true)));

    assertThatThrownBy(
            () ->
                service.normalize(
                    "t1",
                    "tpl1",
                    List.of(RecordDynamicFilter.raw("content", "eq", 123))))
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

    assertThatThrownBy(
            () -> service.normalize("t1", "tpl1", List.of(raw)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not contain both");
  }

  @Test
  void shouldRejectDescendingBetweenBounds() {
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
