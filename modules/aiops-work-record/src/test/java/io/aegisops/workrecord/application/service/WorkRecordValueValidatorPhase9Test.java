package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.port.WorkRecordDictionaryPort;
import io.aegisops.workrecord.application.port.WorkRecordUserPort;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.OptionSource;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WorkRecordValueValidatorPhase9Test {
  private final WorkRecordDictionaryPort dictionaryPort = Mockito.mock(WorkRecordDictionaryPort.class);
  private final WorkRecordUserPort userPort = Mockito.mock(WorkRecordUserPort.class);
  private final WorkRecordValueValidator validator =
      new WorkRecordValueValidator(new ObjectMapper(), dictionaryPort, userPort);

  @Test
  void shouldRejectUnknownField() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    "t1",
                    "v1",
                    List.of(field("content", FieldType.TEXTAREA, false)),
                    "{\"bad\":\"x\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown field: bad");
  }

  @Test
  void shouldRejectInvalidFieldCodeInCustomData() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    "t1",
                    "v1",
                    List.of(field("content", FieldType.TEXTAREA, false)),
                    "{\"bad-key\":\"x\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid fieldCode");
  }

  @Test
  void shouldRejectDisabledFieldWrite() {
    WorkRecordField disabled =
        new WorkRecordField(
            "f1",
            "t1",
            "tpl1",
            "v1",
            "旧字段",
            "oldField",
            FieldType.TEXT,
            false,
            null,
            OptionSource.STATIC,
            null,
            "[]",
            ".properties.oldField",
            true,
            true,
            true,
            false,
            1,
            false,
            OffsetDateTime.now(),
            OffsetDateTime.now());

    assertThatThrownBy(
            () -> validator.validate("t1", "v1", List.of(disabled), "{\"oldField\":\"x\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("field is disabled: oldField");
  }

  @Test
  void shouldRejectMissingRequiredField() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    "t1", "v1", List.of(field("content", FieldType.TEXTAREA, true)), "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("required field is missing: content");
  }

  @Test
  void shouldRejectNumberAsString() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    "t1", "v1", List.of(field("cost", FieldType.NUMBER, false)), "{\"cost\":\"1\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("field must be number: cost");
  }

  @Test
  void shouldRejectInvalidDate() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    "t1", "v1", List.of(field("day", FieldType.DATE, false)), "{\"day\":\"2026/01/01\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("field must be ISO date: day");
  }

  @Test
  void shouldRejectDatetimeWithoutOffset() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    "t1",
                    "v1",
                    List.of(field("startedAt", FieldType.DATETIME, false)),
                    "{\"startedAt\":\"2026-01-01T10:00:00\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("field must be ISO offset datetime: startedAt");
  }

  @Test
  void shouldRejectBooleanAsString() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    "t1", "v1", List.of(field("ok", FieldType.BOOLEAN, false)), "{\"ok\":\"true\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("field must be boolean: ok");
  }

  @Test
  void shouldValidateStaticSelectOptions() {
    WorkRecordField priority =
        field("priority", FieldType.SELECT, false, OptionSource.STATIC, null, "[\"P0\",\"P1\"]");

    validator.validate("t1", "v1", List.of(priority), "{\"priority\":\"P1\"}");
  }

  @Test
  void shouldRejectStaticSelectOptionNotAllowed() {
    WorkRecordField priority =
        field("priority", FieldType.SELECT, false, OptionSource.STATIC, null, "[\"P0\",\"P1\"]");

    assertThatThrownBy(() -> validator.validate("t1", "v1", List.of(priority), "{\"priority\":\"P2\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("field option is not allowed: priority/P2");
  }

  @Test
  void shouldRejectStaticSelectWithoutOptions() {
    WorkRecordField priority =
        field("priority", FieldType.SELECT, false, OptionSource.STATIC, null, "[]");

    assertThatThrownBy(() -> validator.validate("t1", "v1", List.of(priority), "{\"priority\":\"P1\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("static options are required: priority");
  }

  @Test
  void shouldValidateDictSelectValue() {
    WorkRecordField priority =
        field("priority", FieldType.SELECT, false, OptionSource.DICT, "record_priority", "[]");

    validator.validate("t1", "v1", List.of(priority), "{\"priority\":\"P1\"}");

    verify(dictionaryPort).requireEnabledItem("t1", "record_priority", "P1");
  }

  @Test
  void shouldValidateStaticMultiSelectOptions() {
    WorkRecordField tags =
        field(
            "tags",
            FieldType.MULTI_SELECT,
            false,
            OptionSource.STATIC,
            null,
            "[{\"label\":\"A\",\"value\":\"a\"}]");

    validator.validate("t1", "v1", List.of(tags), "{\"tags\":[\"a\"]}");
  }

  @Test
  void shouldRejectMultiSelectNonStringItem() {
    WorkRecordField tags =
        field("tags", FieldType.MULTI_SELECT, false, OptionSource.STATIC, null, "[\"a\"]");

    assertThatThrownBy(() -> validator.validate("t1", "v1", List.of(tags), "{\"tags\":[1]}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("multi_select item must be string: tags");
  }

  @Test
  void shouldValidateDictMultiSelectValues() {
    WorkRecordField tags =
        field("tags", FieldType.MULTI_SELECT, false, OptionSource.DICT, "tag_dict", "[]");

    validator.validate("t1", "v1", List.of(tags), "{\"tags\":[\"a\",\"b\"]}");

    verify(dictionaryPort).requireEnabledItem("t1", "tag_dict", "a");
    verify(dictionaryPort).requireEnabledItem("t1", "tag_dict", "b");
  }

  @Test
  void shouldValidateUserField() {
    WorkRecordField user = field("assignee", FieldType.USER, false);

    validator.validate("t1", "v1", List.of(user), "{\"assignee\":\"u1\"}");

    verify(userPort).requireActiveUser("t1", "u1");
  }

  @Test
  void shouldRejectFieldTenantMismatch() {
    WorkRecordField field =
        new WorkRecordField(
            "f1",
            "t2",
            "tpl1",
            "v1",
            "内容",
            "content",
            FieldType.TEXTAREA,
            false,
            null,
            OptionSource.STATIC,
            null,
            "[]",
            ".properties.content",
            true,
            true,
            true,
            false,
            0,
            true,
            OffsetDateTime.now(),
            OffsetDateTime.now());

    assertThatThrownBy(() -> validator.validate("t1", "v1", List.of(field), "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("field tenant mismatch: content");
  }

  @Test
  void shouldRejectBlankStringForOptionalNumber() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    "t1",
                    "v1",
                    List.of(field("cost", FieldType.NUMBER, false)),
                    "{\"cost\":\"\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("field must be number: cost");
  }

  @Test
  void shouldRejectBlankStringForOptionalBoolean() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    "t1",
                    "v1",
                    List.of(field("ok", FieldType.BOOLEAN, false)),
                    "{\"ok\":\"\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("field must be boolean: ok");
  }

  @Test
  void shouldRejectBlankStringForOptionalSelect() {
    WorkRecordField priority =
        field("priority", FieldType.SELECT, false, OptionSource.STATIC, null, "[\"P0\",\"P1\"]");

    assertThatThrownBy(
            () -> validator.validate("t1", "v1", List.of(priority), "{\"priority\":\"\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("field must not be blank: priority");
  }

  @Test
  void shouldRejectDictOptionSourceOnTextField() {
    WorkRecordField invalid =
        field("priority", FieldType.TEXT, false, OptionSource.DICT, "record_priority", "[]");

    assertThatThrownBy(
            () -> validator.validate("t1", "v1", List.of(invalid), "{\"priority\":\"P1\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dict optionSource is only allowed");
  }

  @Test
  void shouldAllowEmptyArrayForOptionalMultiSelect() {
    WorkRecordField tags =
        field("tags", FieldType.MULTI_SELECT, false, OptionSource.STATIC, null, "[\"a\",\"b\"]");

    validator.validate("t1", "v1", List.of(tags), "{\"tags\":[]}");
  }

  @Test
  void shouldRejectEmptyArrayForRequiredMultiSelect() {
    WorkRecordField tags =
        field("tags", FieldType.MULTI_SELECT, true, OptionSource.STATIC, null, "[\"a\",\"b\"]");

    assertThatThrownBy(() -> validator.validate("t1", "v1", List.of(tags), "{\"tags\":[]}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("required field is missing: tags");
  }

  private WorkRecordField field(String code, FieldType type, boolean required) {
    return field(code, type, required, OptionSource.STATIC, null, "[]");
  }

  private WorkRecordField field(
      String code,
      FieldType type,
      boolean required,
      OptionSource optionSource,
      String dictCode,
      String optionsJson) {
    return new WorkRecordField(
        "f-" + code,
        "t1",
        "tpl1",
        "v1",
        code,
        code,
        type,
        required,
        null,
        optionSource,
        dictCode,
        optionsJson,
        ".properties." + code,
        true,
        true,
        true,
        false,
        0,
        true,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }
}