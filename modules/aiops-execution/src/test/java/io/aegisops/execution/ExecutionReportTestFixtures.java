package io.aegisops.execution;

import io.aegisops.execution.dto.ExecutionArtifactResponse;
import io.aegisops.execution.dto.ExecutionRunResponse;
import io.aegisops.execution.dto.ExecutionStepResponse;
import java.time.OffsetDateTime;
import java.util.List;

final class ExecutionReportTestFixtures {
  private ExecutionReportTestFixtures() {}

  static ExecutionRunResponse execution(String status) {
    return new ExecutionRunResponse(
        "exec_1",
        "tenant_1",
        "inc_1",
        "plan_1",
        status,
        "live",
        "alice",
        "runner_1",
        null,
        "summary",
        1,
        1,
        null,
        null,
        null,
        1800,
        null,
        null,
        "medium",
        null,
        "normal",
        null,
        null,
        List.of(
            new ExecutionStepResponse(
                "step_1",
                "exec_1",
                "planstep_1",
                1,
                "Webhook remediation",
                "webhook",
                "service",
                "succeeded",
                "{\"ok\":true}",
                null,
                1,
                300,
                1,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now())),
        List.of(
            new ExecutionArtifactResponse(
                "artifact_1",
                "exec_1",
                "step_1",
                "json",
                "webhook-response.json",
                "{\"ok\":true}",
                "{}",
                OffsetDateTime.now())),
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }
}
