package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.execution.dto.ExecutionArtifactResponse;
import io.aegisops.execution.dto.ExecutionAuditEventRecord;
import io.aegisops.execution.dto.ExecutionRunResponse;
import io.aegisops.execution.dto.ExecutionStepResponse;
import io.aegisops.execution.dto.ExecutionVerificationRecord;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutionReportMarkdownBuilderTest {
  @Test
  void buildMarkdownReport() {
    ExecutionReportMarkdownBuilder builder = new ExecutionReportMarkdownBuilder();

    String markdown =
        builder.build(
            execution(),
            List.of(
                new ExecutionVerificationRecord(
                    "exv_1",
                    "tenant_1",
                    "exec_1",
                    null,
                    "after",
                    "service",
                    "order-service",
                    "passed",
                    "service recovered",
                    "{}",
                    "alice",
                    OffsetDateTime.now())),
            List.of(
                new ExecutionAuditEventRecord(
                    "xae_1",
                    "tenant_1",
                    "exec_1",
                    null,
                    "execution_succeeded",
                    "runner_1",
                    "Execution succeeded",
                    "{}",
                    OffsetDateTime.now())));

    assertTrue(markdown.contains("# Execution Report"));
    assertTrue(markdown.contains("order-service"));
    assertTrue(markdown.contains("execution_succeeded"));
    assertTrue(markdown.contains("webhook-response.json"));
  }

  private ExecutionRunResponse execution() {
    return new ExecutionRunResponse(
        "exec_1",
        "tenant_1",
        "inc_1",
        "plan_1",
        "succeeded",
        "live",
        "alice",
        "runner_1",
        null,
        "ok",
        1,
        1,
        null,
        null,
        null,
        300,
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
                "Call webhook",
                "webhook",
                "service",
                "succeeded",
                "{}",
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
