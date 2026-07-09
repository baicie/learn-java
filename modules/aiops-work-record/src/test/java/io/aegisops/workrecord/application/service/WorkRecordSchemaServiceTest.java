package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.OptionSource;
import org.junit.jupiter.api.Test;

class WorkRecordSchemaServiceTest {
  private final WorkRecordSchemaService service = new WorkRecordSchemaService(new ObjectMapper());

  @Test
  void shouldExtractXWorkRecordFields() {
    var fields =
        service.extractFields(
            """
            {
              "type": "object",
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
                    "exportable": true
                  }
                }
              }
            }
            """);

    assertThat(fields).hasSize(1);
    assertThat(fields.get(0).fieldCode()).isEqualTo("priority");
    assertThat(fields.get(0).fieldType()).isEqualTo(FieldType.SELECT);
    assertThat(fields.get(0).optionSource()).isEqualTo(OptionSource.DICT);
    assertThat(fields.get(0).dictCode()).isEqualTo("record_priority");
  }

  @Test
  void shouldRejectDictFieldWithoutDictCode() {
    assertThatThrownBy(
            () ->
                service.extractFields(
                    """
                    {
                      "type": "object",
                      "properties": {
                        "priority": {
                          "title": "优先级",
                          "x-work-record": {
                            "fieldCode": "priority",
                            "fieldType": "select",
                            "optionSource": "dict"
                          }
                        }
                      }
                    }
                    """))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dictCode is required");
  }
}
