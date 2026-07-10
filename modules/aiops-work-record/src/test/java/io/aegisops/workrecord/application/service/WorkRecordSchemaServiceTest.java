package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.schema.WorkRecordSchemaNormalizer;
import io.aegisops.workrecord.application.schema.WorkRecordSchemaParser;
import io.aegisops.workrecord.application.schema.WorkRecordSchemaValidator;
import org.junit.jupiter.api.Test;

class WorkRecordSchemaServiceTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final WorkRecordSchemaValidator validator = new WorkRecordSchemaValidator();
  private final WorkRecordSchemaParser parser = new WorkRecordSchemaParser(objectMapper, validator);
  private final WorkRecordSchemaNormalizer normalizer =
      new WorkRecordSchemaNormalizer(objectMapper);
  private final WorkRecordSchemaService service =
      new WorkRecordSchemaService(parser, validator, normalizer);

  @Test
  void shouldPrepareSchemaForPublish() throws Exception {
    var document =
        service.prepareForPublish(
            """
            {
              "type": "object",
              "required": ["priority"],
              "properties": {
                "priority": {
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
            """,
            "{\"layout\":\"simple\"}");

    assertThat(document.schemaVersion()).isEqualTo(1);
    assertThat(document.fields()).hasSize(1);
    assertThat(document.fieldIndexJson()).contains("\"fieldCode\":\"priority\"");
    assertThat(document.normalizedSchemaJson()).contains("\"x-work-record-schema-version\":1");

    var designer = objectMapper.readTree(document.normalizedDesignerJson());
    assertThat(designer.path("layout").asText()).isEqualTo("simple");
  }

  @Test
  void shouldRejectFlatProtocolBeforePublish() {
    assertThatThrownBy(
            () ->
                service.prepareForPublish(
                    """
                    {
                      "type": "object",
                      "properties": {
                        "priority": {
                          "title": "优先级",
                          "x-work-record-field-code": "priority"
                        }
                      }
                    }
                    """,
                    "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("flat work-record schema extension is forbidden");
  }

  @Test
  void shouldRejectUnsupportedFieldType() {
    assertThatThrownBy(
            () ->
                service.prepareForPublish(
                    """
                    {
                      "type": "object",
                      "properties": {
                        "priority": {
                          "x-work-record": {
                            "fieldCode": "priority",
                            "fieldType": "radio"
                          }
                        }
                      }
                    }
                    """,
                    "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported fieldType");
  }
}
