package io.aegisops.workrecord;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkRecordFieldValidatorTest {

  // === Reserved field code ===

  @Test
  void reservedFieldCode_isRejected() {
    for (String reserved :
        List.of("id", "title", "status", "tenant_id", "custom_data_json", "record_time")) {
      assertThatThrownBy(() -> WorkRecordFieldValidator.validateFieldCodeNotReserved(reserved))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("reserved");
    }
  }

  @Test
  void normalFieldCode_isAccepted() {
    assertThatCode(
            () -> {
              WorkRecordFieldValidator.validateFieldCodeNotReserved("inspector");
              WorkRecordFieldValidator.validateFieldCodeNotReserved("change_id");
              WorkRecordFieldValidator.validateFieldCodeNotReserved(null);
            })
        .doesNotThrowAnyException();
  }

  // === Unknown field rejection ===

  @Test
  void unknownField_inCustomData_isRejected() {
    WorkRecordField field = field("f1", "inspector", "text", false, true);
    assertThatThrownBy(
            () ->
                WorkRecordFieldValidator.validateAgainstTemplate(
                    List.of(field), "{\"inspector\":\"liuzhiwei\",\"unknown_key\":\"bad\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown_key")
        .hasMessageContaining("not defined in template");
  }

  @Test
  void builtinContainer_isAllowed() {
    WorkRecordField field = field("f1", "inspector", "text", true, true);
    assertThatCode(
            () ->
                WorkRecordFieldValidator.validateAgainstTemplate(
                    List.of(field), "{\"__builtin__\":{\"inspector\":\"liuzhiwei\"}}"))
        .doesNotThrowAnyException();
  }

  // === Required field ===

  @Test
  void missingRequiredField_throws() {
    WorkRecordField required = field("f1", "inspector", "text", true, true);
    assertThatThrownBy(
            () -> WorkRecordFieldValidator.validateAgainstTemplate(List.of(required), "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("required");
  }

  @Test
  void emptyStringRequiredField_throws() {
    WorkRecordField required = field("f1", "inspector", "text", true, true);
    assertThatThrownBy(
            () ->
                WorkRecordFieldValidator.validateAgainstTemplate(
                    List.of(required), "{\"inspector\":\"\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("required");
  }

  @Test
  void nonRequiredField_emptyIsAllowed() {
    WorkRecordField optional = field("f1", "note", "text", false, true);
    assertThatCode(() -> WorkRecordFieldValidator.validateAgainstTemplate(List.of(optional), "{}"))
        .doesNotThrowAnyException();
  }

  // === Disabled field ===

  @Test
  void disabledField_withValue_isRejected() {
    WorkRecordField disabled = field("f1", "archived_field", "text", false, false);
    assertThatThrownBy(
            () ->
                WorkRecordFieldValidator.validateAgainstTemplate(
                    List.of(disabled), "{\"archived_field\":\"some value\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("disabled");
  }

  @Test
  void disabledField_nullValue_isAllowed() {
    WorkRecordField disabled = field("f1", "archived_field", "text", false, false);
    assertThatCode(() -> WorkRecordFieldValidator.validateAgainstTemplate(List.of(disabled), "{}"))
        .doesNotThrowAnyException();
  }

  // === Value type and format ===

  @Test
  void numberAcceptsJsonNumber() {
    WorkRecordField num = field("f1", "duration", "number", false, true);
    assertThatCode(
            () ->
                WorkRecordFieldValidator.validateAgainstTemplate(List.of(num), "{\"duration\":42}"))
        .doesNotThrowAnyException();
  }

  @Test
  void numberRejectsString() {
    WorkRecordField num = field("f1", "duration", "number", false, true);
    assertThatThrownBy(
            () ->
                WorkRecordFieldValidator.validateAgainstTemplate(
                    List.of(num), "{\"duration\":\"42\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expects number");
  }

  @Test
  void switchAcceptsBoolean() {
    WorkRecordField sw = field("f1", "rolled_back", "switch", false, true);
    assertThatCode(
            () ->
                WorkRecordFieldValidator.validateAgainstTemplate(
                    List.of(sw), "{\"rolled_back\":true}"))
        .doesNotThrowAnyException();
  }

  @Test
  void switchRejectsString() {
    WorkRecordField sw = field("f1", "rolled_back", "switch", false, true);
    assertThatThrownBy(
            () ->
                WorkRecordFieldValidator.validateAgainstTemplate(
                    List.of(sw), "{\"rolled_back\":\"yes\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expects boolean");
  }

  // === Select with options validation ===

  @Test
  void select_validOptionInStaticList_isAccepted() {
    WorkRecordField select =
        selectField(
            "f1",
            "env",
            "static",
            "[{\"label\":\"prod\",\"value\":\"prod\"},{\"label\":\"test\",\"value\":\"test\"}]");
    assertThatCode(
            () ->
                WorkRecordFieldValidator.validateAgainstTemplate(
                    List.of(select), "{\"env\":\"prod\"}"))
        .doesNotThrowAnyException();
  }

  @Test
  void select_invalidOptionInStaticList_isRejected() {
    WorkRecordField select =
        selectField("f1", "env", "static", "[{\"label\":\"prod\",\"value\":\"prod\"}]");
    assertThatThrownBy(
            () ->
                WorkRecordFieldValidator.validateAgainstTemplate(
                    List.of(select), "{\"env\":\"staging\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("staging")
        .hasMessageContaining("not in allowed options");
  }

  @Test
  void select_dictSource_emptyOptions_isAllowed() {
    // dict source with empty options_json: no value list to validate against, pass through
    WorkRecordField select = selectField("f1", "env", "dict", "[]");
    assertThatCode(
            () ->
                WorkRecordFieldValidator.validateAgainstTemplate(
                    List.of(select), "{\"env\":\"any_value\"}"))
        .doesNotThrowAnyException();
  }

  // === Multi-select validation ===

  @Test
  void multiSelect_allValid_isAccepted() {
    WorkRecordField multi =
        multiSelectField(
            "f1",
            "tags",
            "static",
            "[{\"label\":\"a\",\"value\":\"a\"},{\"label\":\"b\",\"value\":\"b\"}]");
    assertThatCode(
            () ->
                WorkRecordFieldValidator.validateAgainstTemplate(
                    List.of(multi), "{\"tags\":[\"a\",\"b\"]}"))
        .doesNotThrowAnyException();
  }

  @Test
  void multiSelect_oneInvalid_isRejected() {
    WorkRecordField multi =
        multiSelectField("f1", "tags", "static", "[{\"label\":\"a\",\"value\":\"a\"}]");
    assertThatThrownBy(
            () ->
                WorkRecordFieldValidator.validateAgainstTemplate(
                    List.of(multi), "{\"tags\":[\"a\",\"c\"]}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not in allowed options");
  }

  @Test
  void multiSelect_nonStringElement_isRejected() {
    WorkRecordField multi =
        multiSelectField("f1", "tags", "static", "[{\"label\":\"a\",\"value\":\"a\"}]");
    assertThatThrownBy(
            () ->
                WorkRecordFieldValidator.validateAgainstTemplate(
                    List.of(multi), "{\"tags\":[\"a\",42]}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expects all elements to be strings");
  }

  // === Date format ===

  @Test
  void date_validIsoFormat_isAccepted() {
    WorkRecordField date = field("f1", "check_date", "date", false, true);
    assertThatCode(
            () ->
                WorkRecordFieldValidator.validateAgainstTemplate(
                    List.of(date), "{\"check_date\":\"2026-07-07\"}"))
        .doesNotThrowAnyException();
  }

  @Test
  void date_invalidFormat_isRejected() {
    WorkRecordField date = field("f1", "check_date", "date", false, true);
    assertThatThrownBy(
            () ->
                WorkRecordFieldValidator.validateAgainstTemplate(
                    List.of(date), "{\"check_date\":\"tomorrow\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expects ISO date string");
  }

  // === DateTime format ===

  @Test
  void datetime_validIsoFormat_isAccepted() {
    WorkRecordField dt = field("f1", "inspection_time", "datetime", false, true);
    assertThatCode(
            () ->
                WorkRecordFieldValidator.validateAgainstTemplate(
                    List.of(dt), "{\"inspection_time\":\"2026-07-07T10:30:00+08:00\"}"))
        .doesNotThrowAnyException();
  }

  @Test
  void datetime_invalidFormat_isRejected() {
    WorkRecordField dt = field("f1", "inspection_time", "datetime", false, true);
    assertThatThrownBy(
            () ->
                WorkRecordFieldValidator.validateAgainstTemplate(
                    List.of(dt), "{\"inspection_time\":\"not-a-date\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expects ISO datetime");
  }

  // === Empty template ===

  @Test
  void emptyTemplate_withEmptyJson_isAllowed() {
    assertThatCode(() -> WorkRecordFieldValidator.validateAgainstTemplate(List.of(), "{}"))
        .doesNotThrowAnyException();
  }

  @Test
  void emptyTemplate_withNonEmptyJson_isRejected() {
    assertThatThrownBy(
            () ->
                WorkRecordFieldValidator.validateAgainstTemplate(
                    List.of(), "{\"any_field\":\"any value\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not allowed when template has no fields");
  }

  // === Helpers ===

  private WorkRecordField field(
      String id, String code, String type, boolean required, boolean enabled) {
    return new WorkRecordField(
        id,
        "t1",
        "tpl1",
        code,
        code,
        type,
        required,
        null,
        "static",
        null,
        "[]",
        true,
        false,
        false,
        0,
        enabled,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private WorkRecordField selectField(
      String id, String code, String optionSource, String optionsJson) {
    return selectField(id, code, "select", optionSource, optionsJson);
  }

  private WorkRecordField multiSelectField(
      String id, String code, String optionSource, String optionsJson) {
    return selectField(id, code, "multi_select", optionSource, optionsJson);
  }

  private WorkRecordField selectField(
      String id, String code, String fieldType, String optionSource, String optionsJson) {
    return new WorkRecordField(
        id,
        "t1",
        "tpl1",
        code,
        code,
        fieldType,
        false,
        null,
        optionSource,
        optionSource.equals("dict") ? code : null,
        optionsJson,
        true,
        false,
        false,
        0,
        true,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }
}
