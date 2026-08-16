package io.aegisops.workrecord.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AiGenerationResponseTest {
  @Test
  void responseDoesNotExposeInternalInputSnapshotOrHash() {
    assertThat(
            Arrays.stream(AiGenerationResponse.class.getRecordComponents())
                .map(component -> component.getName())
                .toList())
        .doesNotContain("tenantId", "inputHash", "inputJson")
        .contains("outputMarkdown", "providerWorkflowVersion", "fallbackReason");
  }
}
