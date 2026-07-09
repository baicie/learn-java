package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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

/**
 * 覆盖 Phase 8/9 设计稿“运行态”侧重点：required 缺失、未知字段、datetime 类型、dict 启用项校验。既有
 * {@link WorkRecordValueValidatorTest} 已覆盖了大部分规则，这里只补运行态关键路径，避免重复。
 */
class WorkRecordValueValidatorRuntimeTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final WorkRecordDictionaryPort dictionaryPort = mock(WorkRecordDictionaryPort.class);
  private final WorkRecordUserPort userPort = mock(WorkRecordUserPort.class);
  private final WorkRecordValueValidator validator =
      new WorkRecordValueValidator(objectMapper, dictionaryPort, userPort);

  @Test
  void shouldValidateRequiredField() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    "t1",
                    "v1",
                    List.of(field("content", FieldType.TEXTAREA, true)),
                    "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("required field is missing: content");
  }

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
  void shouldValidateDatetimeAsOffsetDateTime() {
    assertThatCode(
            () ->
                validator.validate(
                    "t1",
                    "v1",
                    List.of(field("startedAt", FieldType.DATETIME, true)),
                    "{\"startedAt\":\"2026-01-01T00:00:00Z\"}"))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldValidateDictValueIncludingHistoricalRuntimeSubmit() {
    WorkRecordField selectField =
        new WorkRecordField(
            "f1",
            "t1",
            "tpl1",
            "v1",
            "优先级",
            "priority",
            FieldType.SELECT,
            true,
            null,
            OptionSource.DICT,
            "record_priority",
            "[]",
            ".properties.priority",
            true,
            true,
            true,
            false,
            0,
            true,
            OffsetDateTime.now(),
            OffsetDateTime.now());

    assertThatCode(
            () ->
                validator.validate(
                    "t1", "v1", List.of(selectField), "{\"priority\":\"P1\"}"))
        .doesNotThrowAnyException();
    verify(dictionaryPort).requireEnabledItem("t1", "record_priority", "P1");
  }

  private WorkRecordField field(String code, FieldType type, boolean required) {
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
}