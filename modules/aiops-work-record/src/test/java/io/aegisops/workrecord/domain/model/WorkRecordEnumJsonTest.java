package io.aegisops.workrecord.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class WorkRecordEnumJsonTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldSerializeRecordStatusAsLowercaseContractValue() throws Exception {
    assertThat(objectMapper.writeValueAsString(RecordStatus.DONE)).isEqualTo("\"done\"");
  }

  @Test
  void shouldSerializeTemplateStatusAsLowercaseContractValue() throws Exception {
    assertThat(objectMapper.writeValueAsString(TemplateStatus.PUBLISHED))
        .isEqualTo("\"published\"");
  }

  @Test
  void shouldSerializeFieldTypeAsContractValue() throws Exception {
    assertThat(objectMapper.writeValueAsString(FieldType.MULTI_SELECT))
        .isEqualTo("\"multi_select\"");
  }

  @Test
  void shouldSerializeOptionSourceAsContractValue() throws Exception {
    assertThat(objectMapper.writeValueAsString(OptionSource.DICT)).isEqualTo("\"dict\"");
  }
}
