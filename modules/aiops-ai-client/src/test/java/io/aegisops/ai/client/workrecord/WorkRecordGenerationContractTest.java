package io.aegisops.ai.client.workrecord;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkRecordGenerationContractTest {
  @Test
  void carriesActorAndProviderTraceMetadata() {
    var request =
        new WorkRecordGenerationRequest(
            "work-record-generation.v1",
            "monthly_report",
            "tenant-1",
            "2026-07",
            "user-1",
            null,
            null,
            "zh-CN",
            "work-record-monthly-v2",
            List.of(),
            Map.of(),
            "trace-1");
    var response =
        new WorkRecordGenerationResponse(
            "work-record-generation.v1",
            "dify",
            "dify-workflow",
            "work-record-monthly-v2",
            "# 月报",
            List.of("warning"),
            "run-1",
            "workflow-1",
            "version-1",
            1200L,
            321L,
            null,
            Map.of());

    assertThat(request.actorId()).isEqualTo("user-1");
    assertThat(response.providerRunId()).isEqualTo("run-1");
    assertThat(response.providerWorkflowId()).isEqualTo("workflow-1");
    assertThat(response.providerWorkflowVersion()).isEqualTo("version-1");
    assertThat(response.providerDurationMs()).isEqualTo(1200L);
    assertThat(response.providerTotalTokens()).isEqualTo(321L);
    assertThat(response.warnings()).containsExactly("warning");
  }
}
