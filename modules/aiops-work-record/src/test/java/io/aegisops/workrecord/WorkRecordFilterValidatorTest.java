package io.aegisops.workrecord;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aegisops.workrecord.domain.model.DynamicFieldFilter;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.domain.rule.WorkRecordFilterValidator;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WorkRecordFilterValidatorTest {

  private WorkRecordField textField(String code, boolean filterable) {
    return new WorkRecordField(
        "f1",
        "t1",
        "tpl1",
        code,
        code,
        "text",
        false,
        null,
        "static",
        null,
        "[]",
        true,
        filterable,
        true,
        false,
        0,
        true,
        ".properties." + code,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private WorkRecordField textFieldDisabled(String code) {
    return new WorkRecordField(
        "f1",
        "t1",
        "tpl1",
        code,
        code,
        "text",
        false,
        null,
        "static",
        null,
        "[]",
        true,
        true,
        true,
        false,
        0,
        false, // enabled = false
        ".properties." + code,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private WorkRecordField numberField(String code, boolean filterable) {
    return new WorkRecordField(
        "f2",
        "t1",
        "tpl1",
        code,
        code,
        "number",
        false,
        null,
        "static",
        null,
        "[]",
        true,
        filterable,
        true,
        false,
        0,
        true,
        ".properties." + code,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private WorkRecordField dateField(String code, boolean filterable) {
    return new WorkRecordField(
        "f3",
        "t1",
        "tpl1",
        code,
        code,
        "date",
        false,
        null,
        "static",
        null,
        "[]",
        true,
        filterable,
        true,
        false,
        0,
        true,
        ".properties." + code,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private WorkRecordField selectField(String code, boolean filterable) {
    return new WorkRecordField(
        "f4",
        "t1",
        "tpl1",
        code,
        code,
        "select",
        false,
        null,
        "static",
        null,
        "[{\"label\":\"A\",\"value\":\"a\"},{\"label\":\"B\",\"value\":\"b\"}]",
        true,
        filterable,
        true,
        false,
        0,
        true,
        ".properties." + code,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private WorkRecordField boolField(String code, boolean filterable) {
    return new WorkRecordField(
        "f5",
        "t1",
        "tpl1",
        code,
        code,
        "switch",
        false,
        null,
        "static",
        null,
        "[]",
        true,
        filterable,
        true,
        false,
        0,
        true,
        ".properties." + code,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private WorkRecordField userField(String code, boolean filterable) {
    return new WorkRecordField(
        "f6",
        "t1",
        "tpl1",
        code,
        code,
        "user",
        false,
        null,
        "static",
        null,
        "[]",
        true,
        filterable,
        true,
        false,
        0,
        true,
        ".properties." + code,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private WorkRecordField multiSelectField(String code, boolean filterable) {
    return new WorkRecordField(
        "f7",
        "t1",
        "tpl1",
        code,
        code,
        "multi_select",
        false,
        null,
        "static",
        null,
        "[{\"label\":\"X\",\"value\":\"x\"},{\"label\":\"Y\",\"value\":\"y\"}]",
        true,
        filterable,
        true,
        false,
        0,
        true,
        ".properties." + code,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private DynamicFieldFilter filter(
      String fieldCode, String operator, Object value, List<Object> values) {
    return new DynamicFieldFilter(fieldCode, operator, value, values);
  }

  @Nested
  class Validate {

    @Test
    void nullFilters_isAllowed() {
      assertThatCode(() -> WorkRecordFilterValidator.validate(List.of(), null))
          .doesNotThrowAnyException();
    }

    @Test
    void emptyFilters_isAllowed() {
      assertThatCode(() -> WorkRecordFilterValidator.validate(List.of(), List.of()))
          .doesNotThrowAnyException();
    }

    @Test
    void validTextFilter_isAllowed() {
      List<WorkRecordField> fields = List.of(textField("memo", true));
      List<DynamicFieldFilter> filters = List.of(filter("memo", "contains", "hello", null));
      assertThatCode(() -> WorkRecordFilterValidator.validate(fields, filters))
          .doesNotThrowAnyException();
    }

    @Test
    void validNumberFilter_isAllowed() {
      List<WorkRecordField> fields = List.of(numberField("count", true));
      List<DynamicFieldFilter> filters = List.of(filter("count", "gte", 10, null));
      assertThatCode(() -> WorkRecordFilterValidator.validate(fields, filters))
          .doesNotThrowAnyException();
    }

    @Test
    void validDateFilter_isAllowed() {
      List<WorkRecordField> fields = List.of(dateField("check_date", true));
      List<DynamicFieldFilter> filters =
          List.of(filter("check_date", "between", null, List.of("2026-01-01", "2026-12-31")));
      assertThatCode(() -> WorkRecordFilterValidator.validate(fields, filters))
          .doesNotThrowAnyException();
    }

    @Test
    void validSelectFilter_isAllowed() {
      List<WorkRecordField> fields = List.of(selectField("env", true));
      List<DynamicFieldFilter> filters = List.of(filter("env", "in", null, List.of("a", "b")));
      assertThatCode(() -> WorkRecordFilterValidator.validate(fields, filters))
          .doesNotThrowAnyException();
    }

    @Test
    void validBoolFilter_isAllowed() {
      List<WorkRecordField> fields = List.of(boolField("enabled", true));
      List<DynamicFieldFilter> filters = List.of(filter("enabled", "eq", true, null));
      assertThatCode(() -> WorkRecordFilterValidator.validate(fields, filters))
          .doesNotThrowAnyException();
    }

    @Test
    void validUserFilter_isAllowed() {
      List<WorkRecordField> fields = List.of(userField("assignee", true));
      List<DynamicFieldFilter> filters = List.of(filter("assignee", "eq", "user1", null));
      assertThatCode(() -> WorkRecordFilterValidator.validate(fields, filters))
          .doesNotThrowAnyException();
    }

    @Test
    void validMultiSelectFilter_isAllowed() {
      List<WorkRecordField> fields = List.of(multiSelectField("tags", true));
      List<DynamicFieldFilter> filters = List.of(filter("tags", "in", null, List.of("x", "y")));
      assertThatCode(() -> WorkRecordFilterValidator.validate(fields, filters))
          .doesNotThrowAnyException();
    }

    @Test
    void existsOperator_noValue_isAllowed() {
      List<WorkRecordField> fields = List.of(textField("memo", true));
      List<DynamicFieldFilter> filters = List.of(filter("memo", "exists", null, null));
      assertThatCode(() -> WorkRecordFilterValidator.validate(fields, filters))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  class FieldCodeValidation {

    @Test
    void unknownFieldCode_isRejected() {
      List<WorkRecordField> fields = List.of(textField("memo", true));
      List<DynamicFieldFilter> filters = List.of(filter("unknown_field", "contains", "test", null));
      assertThatThrownBy(() -> WorkRecordFilterValidator.validate(fields, filters))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("unknown_field")
          .hasMessageContaining("not defined in template");
    }

    @Test
    void disabledField_isRejected() {
      List<WorkRecordField> fields = List.of(textFieldDisabled("memo"));
      List<DynamicFieldFilter> filters = List.of(filter("memo", "contains", "test", null));
      assertThatThrownBy(() -> WorkRecordFilterValidator.validate(fields, filters))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not defined in template or is disabled");
    }

    @Test
    void nonFilterableField_isRejected() {
      List<WorkRecordField> fields = List.of(textField("memo", false));
      List<DynamicFieldFilter> filters = List.of(filter("memo", "contains", "test", null));
      assertThatThrownBy(() -> WorkRecordFilterValidator.validate(fields, filters))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not filterable");
    }
  }

  @Nested
  class OperatorValidation {

    @Test
    void text_invalidOperator_isRejected() {
      List<WorkRecordField> fields = List.of(textField("memo", true));
      List<DynamicFieldFilter> filters = List.of(filter("memo", "gte", "test", null));
      assertThatThrownBy(() -> WorkRecordFilterValidator.validate(fields, filters))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not supported for fieldType");
    }

    @Test
    void number_invalidOperator_isRejected() {
      List<WorkRecordField> fields = List.of(numberField("count", true));
      List<DynamicFieldFilter> filters = List.of(filter("count", "contains", 10, null));
      assertThatThrownBy(() -> WorkRecordFilterValidator.validate(fields, filters))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not supported for fieldType");
    }

    @Test
    void select_invalidOperator_isRejected() {
      List<WorkRecordField> fields = List.of(selectField("env", true));
      List<DynamicFieldFilter> filters = List.of(filter("env", "contains", "a", null));
      assertThatThrownBy(() -> WorkRecordFilterValidator.validate(fields, filters))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not supported for fieldType");
    }

    @Test
    void multiSelect_invalidOperator_isRejected() {
      List<WorkRecordField> fields = List.of(multiSelectField("tags", true));
      List<DynamicFieldFilter> filters = List.of(filter("tags", "eq", "x", null));
      assertThatThrownBy(() -> WorkRecordFilterValidator.validate(fields, filters))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not supported for fieldType");
    }
  }

  @Nested
  class ValueTypeValidation {

    @Test
    void number_rejectsString() {
      List<WorkRecordField> fields = List.of(numberField("count", true));
      List<DynamicFieldFilter> filters = List.of(filter("count", "eq", "not a number", null));
      assertThatThrownBy(() -> WorkRecordFilterValidator.validate(fields, filters))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("expects number value");
    }

    @Test
    void bool_rejectsString() {
      List<WorkRecordField> fields = List.of(boolField("enabled", true));
      List<DynamicFieldFilter> filters = List.of(filter("enabled", "eq", "true", null));
      assertThatThrownBy(() -> WorkRecordFilterValidator.validate(fields, filters))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("expects boolean value");
    }

    @Test
    void inOperator_requiresValues() {
      List<WorkRecordField> fields = List.of(selectField("env", true));
      List<DynamicFieldFilter> filters = List.of(filter("env", "in", null, null));
      assertThatThrownBy(() -> WorkRecordFilterValidator.validate(fields, filters))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("requires 'values' array");
    }

    @Test
    void betweenOperator_requiresTwoValues() {
      List<WorkRecordField> fields = List.of(numberField("count", true));
      List<DynamicFieldFilter> filters = List.of(filter("count", "between", null, List.of(1)));
      assertThatThrownBy(() -> WorkRecordFilterValidator.validate(fields, filters))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("requires two values");
    }
  }

  @Nested
  class MultipleFilters {

    @Test
    void mixedValidFilters_areAllowed() {
      List<WorkRecordField> fields =
          List.of(
              textField("memo", true), numberField("count", true), dateField("check_date", true));
      List<DynamicFieldFilter> filters =
          List.of(
              filter("memo", "contains", "hello", null),
              filter("count", "gte", 10, null),
              filter("check_date", "eq", "2026-07-08", null));
      assertThatCode(() -> WorkRecordFilterValidator.validate(fields, filters))
          .doesNotThrowAnyException();
    }

    @Test
    void oneInvalidFilter_failsWithFirstError() {
      List<WorkRecordField> fields = List.of(textField("memo", true), numberField("count", true));
      List<DynamicFieldFilter> filters =
          List.of(
              filter("memo", "contains", "hello", null),
              filter("unknown_field", "eq", "bad", null));
      assertThatThrownBy(() -> WorkRecordFilterValidator.validate(fields, filters))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("unknown_field");
    }
  }
}
