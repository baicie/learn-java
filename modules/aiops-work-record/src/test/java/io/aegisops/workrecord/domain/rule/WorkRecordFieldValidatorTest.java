package io.aegisops.workrecord.domain.rule;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.OptionSource;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WorkRecordFieldValidatorTest {

  @Test
  void validatesReservedCodesAndEmptyTemplates() {
    assertThatCode(() -> WorkRecordFieldValidator.validateFieldCodeNotReserved("summary"))
        .doesNotThrowAnyException();
    assertThatCode(() -> WorkRecordFieldValidator.validateAgainstTemplate(null, null))
        .doesNotThrowAnyException();
    assertThatCode(() -> WorkRecordFieldValidator.validateAgainstTemplate(List.of(), "{}"))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> WorkRecordFieldValidator.validateFieldCodeNotReserved("title"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reserved");
    assertThatThrownBy(
            () -> WorkRecordFieldValidator.validateAgainstTemplate(List.of(), "{\"x\":1}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no fields");
  }

  @Test
  void acceptsAllSupportedTypesFromBuiltinContainer() {
    List<WorkRecordField> fields =
        List.of(
            field("hours", FieldType.NUMBER, true, true, null),
            field("done", FieldType.BOOLEAN, true, true, null),
            field("priority", FieldType.SELECT, true, true, options()),
            field("labels", FieldType.MULTI_SELECT, false, true, options()),
            field("workday", FieldType.DATE, true, true, null),
            field("startedAt", FieldType.DATETIME, true, true, null),
            field("owner", FieldType.USER, true, true, null),
            field("summary", FieldType.TEXT, true, true, null),
            field("detail", FieldType.TEXTAREA, false, true, null));
    String json =
        "{\"__builtin__\":{\"hours\":8,\"done\":true,\"priority\":\"P1\","
            + "\"labels\":[\"P1\",\"P2\"],\"workday\":\"2026-07-16\","
            + "\"startedAt\":\"2026-07-16T10:30:00+08:00\",\"owner\":\"user-1\","
            + "\"summary\":42}}";

    assertThatCode(() -> WorkRecordFieldValidator.validateAgainstTemplate(fields, json))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @MethodSource("invalidValues")
  void rejectsInvalidValues(WorkRecordField field, String json, String message) {
    assertThatThrownBy(() -> WorkRecordFieldValidator.validateAgainstTemplate(List.of(field), json))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(message);
  }

  static List<Arguments> invalidValues() {
    return List.of(
        Arguments.of(
            field("hours", FieldType.NUMBER, false, true, null), json("hours", "true"), "number"),
        Arguments.of(
            field("done", FieldType.BOOLEAN, false, true, null), json("done", "1"), "boolean"),
        Arguments.of(
            field("priority", FieldType.SELECT, false, true, options()),
            json("priority", "1"),
            "string"),
        Arguments.of(
            field("priority", FieldType.SELECT, false, true, options()),
            json("priority", "\"P3\""),
            "allowed options"),
        Arguments.of(
            field("labels", FieldType.MULTI_SELECT, false, true, options()),
            json("labels", "\"P1\""),
            "array"),
        Arguments.of(
            field("labels", FieldType.MULTI_SELECT, false, true, options()),
            json("labels", "[1]"),
            "strings"),
        Arguments.of(
            field("labels", FieldType.MULTI_SELECT, false, true, options()),
            json("labels", "[\"P3\"]"),
            "allowed options"),
        Arguments.of(
            field("workday", FieldType.DATE, false, true, null), json("workday", "1"), "ISO date"),
        Arguments.of(
            field("workday", FieldType.DATE, false, true, null),
            json("workday", "\"16/07/2026\""),
            "ISO date"),
        Arguments.of(
            field("startedAt", FieldType.DATETIME, false, true, null),
            json("startedAt", "1"),
            "datetime"),
        Arguments.of(
            field("startedAt", FieldType.DATETIME, false, true, null),
            json("startedAt", "\"today\""),
            "datetime"),
        Arguments.of(
            field("owner", FieldType.USER, false, true, null), json("owner", "true"), "user id"),
        Arguments.of(
            field("summary", FieldType.TEXT, false, true, null), json("summary", "[]"), "scalar"));
  }

  @Test
  void rejectsMalformedUnknownRequiredAndDisabledFields() {
    WorkRecordField required = field("summary", FieldType.TEXT, true, true, null);
    WorkRecordField disabled = field("legacy", FieldType.TEXT, false, false, null);

    assertThatThrownBy(
            () -> WorkRecordFieldValidator.validateAgainstTemplate(List.of(required), "not-json"))
        .hasMessageContaining("valid JSON");
    assertThatThrownBy(
            () -> WorkRecordFieldValidator.validateAgainstTemplate(List.of(required), "[]"))
        .hasMessageContaining("JSON object");
    assertThatThrownBy(
            () ->
                WorkRecordFieldValidator.validateAgainstTemplate(
                    List.of(required), "{\"unknown\":1}"))
        .hasMessageContaining("not defined");
    assertThatThrownBy(
            () ->
                WorkRecordFieldValidator.validateAgainstTemplate(
                    List.of(required), "{\"summary\":\"  \"}"))
        .hasMessageContaining("required");
    assertThatThrownBy(
            () ->
                WorkRecordFieldValidator.validateAgainstTemplate(
                    List.of(disabled), "{\"legacy\":\"value\"}"))
        .hasMessageContaining("disabled");
  }

  @Test
  void ignoresInvalidOrEmptyOptionDefinitions() {
    for (String options : List.of("", "not-json", "{}", "[{\"label\":\"P1\"}]")) {
      WorkRecordField field = field("priority", FieldType.SELECT, false, true, options);
      assertThatCode(
              () ->
                  WorkRecordFieldValidator.validateAgainstTemplate(
                      List.of(field), "{\"priority\":\"anything\"}"))
          .doesNotThrowAnyException();
    }
  }

  private static String json(String code, String value) {
    return "{\"" + code + "\":" + value + "}";
  }

  private static String options() {
    return "[{\"label\":\"高\",\"value\":\"P1\"},{\"label\":\"中\",\"value\":\"P2\"}]";
  }

  private static WorkRecordField field(
      String code, FieldType type, boolean required, boolean enabled, String options) {
    OffsetDateTime now = OffsetDateTime.parse("2026-07-16T00:00:00Z");
    return new WorkRecordField(
        "field-" + code,
        "tenant-1",
        "template-1",
        "version-1",
        code,
        code,
        type,
        required,
        null,
        OptionSource.STATIC,
        null,
        options,
        "$." + code,
        true,
        true,
        true,
        false,
        1,
        enabled,
        now,
        now);
  }
}
