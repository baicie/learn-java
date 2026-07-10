package io.aegisops.workrecord.application.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.FormFieldDescriptor;
import io.aegisops.workrecord.domain.model.OptionSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkRecordSchemaNormalizerTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final WorkRecordSchemaNormalizer normalizer =
      new WorkRecordSchemaNormalizer(objectMapper);

  @Test
  void shouldAddSchemaVersionAndDefaultExtensionFlags() throws Exception {
    String normalized =
        normalizer.normalizeSchema(
            """
            {
              "properties": {
                "content": {
                  "title": "内容",
                  "x-work-record": {
                    "fieldCode": "content",
                    "fieldType": "textarea"
                  }
                }
              }
            }
            """);

    var root = objectMapper.readTree(normalized);
    assertThat(root.path("type").asText()).isEqualTo("object");
    assertThat(root.path("x-work-record-schema-version").asInt()).isEqualTo(1);

    var ext = root.path("properties").path("content").path("x-work-record");
    assertThat(ext.path("optionSource").asText()).isEqualTo("static");
    assertThat(ext.path("listVisible").asBoolean()).isFalse();
    assertThat(ext.path("filterable").asBoolean()).isFalse();
    assertThat(ext.path("exportable").asBoolean()).isTrue();
    assertThat(ext.path("statistical").asBoolean()).isFalse();
  }

  @Test
  void shouldSerializeFieldIndexInStableOrder() {
    FormFieldDescriptor b =
        new FormFieldDescriptor(
            "B",
            "b",
            FieldType.TEXT,
            false,
            OptionSource.STATIC,
            null,
            "[]",
            ".properties.b",
            true,
            false,
            true,
            false,
            2);

    FormFieldDescriptor a =
        new FormFieldDescriptor(
            "A",
            "a",
            FieldType.TEXT,
            false,
            OptionSource.STATIC,
            null,
            "[]",
            ".properties.a",
            true,
            false,
            true,
            false,
            1);

    String json = normalizer.fieldIndexJson(List.of(b, a));

    assertThat(json.indexOf("\"fieldCode\":\"a\"")).isLessThan(json.indexOf("\"fieldCode\":\"b\""));
  }
}
