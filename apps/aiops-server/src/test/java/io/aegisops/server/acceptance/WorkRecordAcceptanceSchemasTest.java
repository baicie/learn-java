package io.aegisops.server.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class WorkRecordAcceptanceSchemasTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void v1MustContainThreeEnterpriseFields() throws Exception {
    JsonNode root = objectMapper.readTree(WorkRecordAcceptanceSchemas.v1("acceptance_priority"));

    assertThat(fieldCodes(root)).containsExactlyInAnyOrder("summary", "priority", "hours");

    assertThat(
            root.path("properties")
                .path("priority")
                .path("x-work-record")
                .path("dictCode")
                .asText())
        .isEqualTo("acceptance_priority");
  }

  @Test
  void v2MustRemainBackwardCompatibleAndAddNextPlan() throws Exception {
    JsonNode v1 = objectMapper.readTree(WorkRecordAcceptanceSchemas.v1("acceptance_priority"));

    JsonNode v2 = objectMapper.readTree(WorkRecordAcceptanceSchemas.v2("acceptance_priority"));

    assertThat(fieldCodes(v2)).containsAll(fieldCodes(v1)).contains("nextPlan");

    assertThat(
            StreamSupport.stream(v2.path("required").spliterator(), false)
                .map(JsonNode::asText)
                .toList())
        .containsExactlyInAnyOrder("summary", "priority", "hours", "nextPlan");
  }

  private Set<String> fieldCodes(JsonNode root) {
    return StreamSupport.stream(root.path("properties").spliterator(), false)
        .map(field -> field.path("x-work-record").path("fieldCode").asText())
        .collect(Collectors.toSet());
  }
}
