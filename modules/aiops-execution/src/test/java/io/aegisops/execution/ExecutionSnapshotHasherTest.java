package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.dto.ExecutionRunCreateCommand;
import io.aegisops.execution.dto.ExecutionStepCreateCommand;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutionSnapshotHasherTest {
  private final ExecutionSnapshotHasher hasher = new ExecutionSnapshotHasher(new ObjectMapper());

  @Test
  void canonicalSnapshotIgnoresJsonObjectOrderAndInputStepOrder() {
    ExecutionRunCreateCommand run = run("{\"approvedCount\":1,\"status\":\"approved\"}");
    ExecutionStepCreateCommand first = step("step_1", 1, "{\"b\":2,\"a\":1}", "echo one");
    ExecutionStepCreateCommand second = step("step_2", 2, "{\"command\":\"echo two\"}", "echo two");

    String firstHash = hasher.sha256(run, List.of(second, first));
    String secondHash =
        hasher.sha256(
            run("{\"status\":\"approved\",\"approvedCount\":1}"),
            List.of(step("step_1", 1, "{\"a\":1,\"b\":2}", "echo one"), second));

    assertEquals(firstHash, secondHash);
    assertEquals(64, firstHash.length());
  }

  @Test
  void executionFieldTamperingChangesSnapshotHash() {
    ExecutionRunCreateCommand run = run("{\"status\":\"approved\"}");
    ExecutionStepCreateCommand original =
        step("step_1", 1, "{\"command\":\"echo one\"}", "echo one");
    ExecutionStepCreateCommand tampered =
        step("step_1", 1, "{\"command\":\"echo two\"}", "echo two");

    assertNotEquals(hasher.sha256(run, List.of(original)), hasher.sha256(run, List.of(tampered)));
  }

  private ExecutionRunCreateCommand run(String approvalSnapshot) {
    return new ExecutionRunCreateCommand(
        "exec_1",
        "tenant_1",
        "inc_1",
        "plan_1",
        "queued",
        "dry_run",
        "alice",
        2,
        3,
        "exec_0",
        1800,
        "approval_1",
        approvalSnapshot,
        "medium",
        "normal",
        null,
        null,
        null,
        null,
        null);
  }

  private ExecutionStepCreateCommand step(
      String id, int sequence, String payload, String commandSnapshot) {
    return new ExecutionStepCreateCommand(
        id,
        "tenant_1",
        "exec_1",
        "planstep_" + sequence,
        sequence,
        "Step " + sequence,
        "shell",
        "host",
        "queued",
        payload,
        commandSnapshot,
        2,
        300);
  }
}
