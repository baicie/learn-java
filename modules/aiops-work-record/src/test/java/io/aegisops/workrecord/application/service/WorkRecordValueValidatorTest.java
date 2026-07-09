package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.port.WorkRecordDictionaryPort;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.OptionSource;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkRecordValueValidatorTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final WorkRecordDictionaryPort dictionaryPort = mock(WorkRecordDictionaryPort.class);
  private final WorkRecordValueValidator validator =
      new WorkRecordValueValidator(objectMapper, dictionaryPort);

  private WorkRecordField selectField(String code, String dictCode) {
    return new WorkRecordField(
        "f1",
        "tenant1",
        "tpl1",
        "v1",
        "优先级",
        code,
        FieldType.SELECT,
        true,
        null,
        OptionSource.DICT,
        dictCode,
        "[]",
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

  private WorkRecordField numberField(String code) {
    return new WorkRecordField(
        "f1",
        "tenant1",
        "tpl1",
        "v1",
        "数量",
        code,
        FieldType.NUMBER,
        false,
        null,
        OptionSource.STATIC,
        null,
        "[]",
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

  @Test
  void shouldAcceptEmptyCustomData() {
    assertThatCode(() -> validator.validate("tenant1", List.of(), "{}")).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectUnknownField() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    "tenant1",
                    List.of(numberField("count")),
                    "{\"unknown\":\"v\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown field: unknown");
  }

  @Test
  void shouldRejectDisabledField() {
    WorkRecordField disabled =
        new WorkRecordField(
            "f1",
            "tenant1",
            "tpl1",
            "v1",
            "数量",
            "count",
            FieldType.NUMBER,
            false,
            null,
OptionSource.STATIC,
        null,
        "[]",
            ".properties.count",
            true,
            true,
            true,
            false,
            0,
            false,
            OffsetDateTime.now(),
            OffsetDateTime.now());
    assertThatThrownBy(
            () -> validator.validate("tenant1", List.of(disabled), "{\"count\":1}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("disabled: count");
  }

  @Test
  void shouldRejectNumberTypeMismatch() {
    assertThatThrownBy(
            () -> validator.validate("tenant1", List.of(numberField("count")), "{\"count\":\"x\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("number: count");
  }

  @Test
  void shouldAcceptNumberValue() {
    assertThatCode(
            () -> validator.validate("tenant1", List.of(numberField("count")), "{\"count\":3}"))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldValidateDictValueViaPort() {
    WorkRecordField field = selectField("priority", "record_priority");
    assertThatCode(
            () -> validator.validate("tenant1", List.of(field), "{\"priority\":\"P1\"}"))
        .doesNotThrowAnyException();
    verify(dictionaryPort).requireEnabledItem("tenant1", "record_priority", "P1");
  }

  @Test
  void shouldRejectMissingDictCode() {
    WorkRecordField field = selectField("priority", null);
    assertThatThrownBy(
            () -> validator.validate("tenant1", List.of(field), "{\"priority\":\"P1\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dictCode is required");
    verify(dictionaryPort, never()).requireEnabledItem("tenant1", null, "P1");
  }

  @Test
  void shouldRejectMissingRequiredField() {
    assertThatThrownBy(
            () -> validator.validate("tenant1", List.of(selectField("priority", "dict")), "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("required field is missing: priority");
  }

  @Test
  void shouldRejectInvalidJson() {
    assertThatThrownBy(
            () -> validator.validate("tenant1", List.of(), "not-a-json"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid customDataJson");
  }

  @Test
  void shouldRejectNonObjectRoot() {
    assertThatThrownBy(() -> validator.validate("tenant1", List.of(), "[1,2,3]"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be object");
  }
}
