package io.aegisops.workrecord.application.schema;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class WorkRecordSchemaValidatorTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final WorkRecordSchemaValidator validator = new WorkRecordSchemaValidator();

  @Test
  void shouldRejectReservedFieldCode() throws Exception {
    var ext =
        objectMapper.readTree(
            """
            {
              "fieldCode": "tenant_id",
              "fieldType": "text"
            }
            """);

    assertThatThrownBy(() -> validator.validateFieldExtension(ext, ".properties.bad"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reserved fieldCode");
  }

  @Test
  void shouldRejectInvalidFieldCode() throws Exception {
    var ext =
        objectMapper.readTree(
            """
            {
              "fieldCode": "1bad",
              "fieldType": "text"
            }
            """);

    assertThatThrownBy(() -> validator.validateFieldExtension(ext, ".properties.bad"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid fieldCode");
  }

  @Test
  void shouldRejectUnsupportedFieldType() throws Exception {
    var ext =
        objectMapper.readTree(
            """
            {
              "fieldCode": "priority",
              "fieldType": "radio"
            }
            """);

    assertThatThrownBy(() -> validator.validateFieldExtension(ext, ".properties.priority"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported fieldType");
  }

  @Test
  void shouldRejectDictWithoutDictCode() throws Exception {
    var ext =
        objectMapper.readTree(
            """
            {
              "fieldCode": "priority",
              "fieldType": "select",
              "optionSource": "dict"
            }
            """);

    assertThatThrownBy(() -> validator.validateFieldExtension(ext, ".properties.priority"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dictCode is required");
  }

  @Test
  void shouldRejectDictCodeWhenOptionSourceStatic() throws Exception {
    var ext =
        objectMapper.readTree(
            """
            {
              "fieldCode": "priority",
              "fieldType": "select",
              "optionSource": "static",
              "dictCode": "record_priority"
            }
            """);

    assertThatThrownBy(() -> validator.validateFieldExtension(ext, ".properties.priority"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dictCode is only allowed");
  }

  @Test
  void shouldRejectDictSourceForNonOptionField() throws Exception {
    var ext =
        objectMapper.readTree(
            """
            {
              "fieldCode": "summary",
              "fieldType": "text",
              "optionSource": "dict",
              "dictCode": "record_priority"
            }
            """);

    assertThatThrownBy(() -> validator.validateFieldExtension(ext, ".properties.summary"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("optionSource is only allowed for select/multi_select");
  }

  @Test
  void shouldRejectUnsupportedSchemaVersion() throws Exception {
    var root =
        objectMapper.readTree(
            """
            {
              "type": "object",
              "x-work-record-schema-version": 999
            }
            """);

    assertThatThrownBy(() -> validator.validateRoot(root))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported schema version");
  }
}
