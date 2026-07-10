package io.aegisops.workrecord.application.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.OptionSource;
import org.junit.jupiter.api.Test;

class WorkRecordSchemaParserTest {
  private final WorkRecordSchemaValidator validator = new WorkRecordSchemaValidator();
  private final WorkRecordSchemaParser parser =
      new WorkRecordSchemaParser(new ObjectMapper(), validator);

  @Test
  void shouldParseNestedXWorkRecordFields() {
    var fields =
        parser.parse(
            """
            {
              "type": "object",
              "required": ["priority"],
              "properties": {
                "priority": {
                  "type": "string",
                  "title": "优先级",
                  "x-component": "Select",
                  "x-work-record": {
                    "fieldCode": "priority",
                    "fieldType": "select",
                    "optionSource": "dict",
                    "dictCode": "record_priority",
                    "listVisible": true,
                    "filterable": true,
                    "exportable": true,
                    "statistical": true
                  }
                }
              }
            }
            """);

    assertThat(fields).hasSize(1);
    assertThat(fields.get(0).fieldCode()).isEqualTo("priority");
    assertThat(fields.get(0).fieldName()).isEqualTo("优先级");
    assertThat(fields.get(0).fieldType()).isEqualTo(FieldType.SELECT);
    assertThat(fields.get(0).optionSource()).isEqualTo(OptionSource.DICT);
    assertThat(fields.get(0).dictCode()).isEqualTo("record_priority");
    assertThat(fields.get(0).required()).isTrue();
    assertThat(fields.get(0).listVisible()).isTrue();
    assertThat(fields.get(0).filterable()).isTrue();
    assertThat(fields.get(0).exportable()).isTrue();
    assertThat(fields.get(0).statistical()).isTrue();
  }

  @Test
  void shouldRejectFlatProtocol() {
    assertThatThrownBy(
            () ->
                parser.parse(
                    """
                    {
                      "type": "object",
                      "properties": {
                        "priority": {
                          "title": "优先级",
                          "x-work-record-field-code": "priority",
                          "x-work-record-field-type": "select"
                        }
                      }
                    }
                    """))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("flat work-record schema extension is forbidden");
  }

  @Test
  void shouldIgnorePlainPropertyWithoutXWorkRecord() {
    var fields =
        parser.parse(
            """
            {
              "type": "object",
              "properties": {
                "plain": {
                  "type": "string",
                  "title": "普通 Formily 字段"
                }
              }
            }
            """);

    assertThat(fields).isEmpty();
  }

  @Test
  void shouldParseNestedObjectFields() {
    var fields =
        parser.parse(
            """
            {
              "type": "object",
              "properties": {
                "base": {
                  "type": "object",
                  "required": ["content"],
                  "properties": {
                    "content": {
                      "title": "内容",
                      "x-work-record": {
                        "fieldCode": "content",
                        "fieldType": "textarea",
                        "listVisible": true
                      }
                    }
                  }
                }
              }
            }
            """);

    assertThat(fields).hasSize(1);
    assertThat(fields.get(0).fieldCode()).isEqualTo("content");
    assertThat(fields.get(0).schemaPath()).isEqualTo(".properties.base.properties.content");
    assertThat(fields.get(0).required()).isTrue();
  }

  @Test
  void shouldRejectDuplicatedFieldCode() {
    assertThatThrownBy(
            () ->
                parser.parse(
                    """
                    {
                      "type": "object",
                      "properties": {
                        "a": {
                          "x-work-record": {
                            "fieldCode": "priority",
                            "fieldType": "text"
                          }
                        },
                        "b": {
                          "x-work-record": {
                            "fieldCode": "priority",
                            "fieldType": "text"
                          }
                        }
                      }
                    }
                    """))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicated fieldCode");
  }
}
