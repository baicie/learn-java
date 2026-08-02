package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.security.ExecutionGrantClaims;
import io.aegisops.common.security.ExecutionGrantCodec;
import io.aegisops.execution.dto.ExecutionRunCreateCommand;
import io.aegisops.execution.dto.ExecutionStepCreateCommand;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SignedExecutionGrantProviderTest {
  private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

  @Test
  void issuesRunnerGrantBoundToCanonicalExecutionSnapshot() throws Exception {
    KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    ObjectMapper objectMapper = new ObjectMapper();
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    ExecutionGrantProperties properties = new ExecutionGrantProperties();
    properties.setKeyId("task-grant-v2");
    properties.setQueueWaitSeconds(1800);

    SignedExecutionGrantProvider provider =
        new SignedExecutionGrantProvider(
            properties,
            new ExecutionGrantCodec(objectMapper, clock),
            new ExecutionSnapshotHasher(objectMapper),
            clock,
            keys.getPrivate());

    ExecutionRunCreateCommand run = run();
    List<ExecutionStepCreateCommand> steps = List.of(step());
    IssuedExecutionGrant issued = provider.issue(run, steps);
    ExecutionGrantClaims claims =
        new ExecutionGrantCodec(objectMapper, clock)
            .verify(Map.of("task-grant-v2", keys.getPublic()), issued.token(), "aiops-runner");

    assertEquals("aegisops-app", claims.issuer());
    assertEquals(List.of("runbook:execute"), claims.scopes());
    assertEquals("execution:exec_1", claims.subject());
    assertEquals("tenant_1", claims.tenantId());
    assertEquals("inc_1", claims.incidentId());
    assertEquals("exec_1", claims.executionId());
    assertEquals("plan_1", claims.planId());
    assertEquals("dry_run", claims.mode());
    assertEquals(1800, claims.maxDurationSeconds());
    assertEquals(issued.snapshotSha256(), claims.snapshotSha256());
    assertEquals(NOW.plusSeconds(3600), issued.expiresAt().toInstant());
  }

  private ExecutionRunCreateCommand run() {
    return new ExecutionRunCreateCommand(
        "exec_1",
        "tenant_1",
        "inc_1",
        "plan_1",
        "queued",
        "dry_run",
        "alice",
        1,
        3,
        null,
        1800,
        null,
        "{}",
        "medium",
        "normal",
        null,
        null,
        null,
        null,
        null);
  }

  private ExecutionStepCreateCommand step() {
    return new ExecutionStepCreateCommand(
        "step_1",
        "tenant_1",
        "exec_1",
        "planstep_1",
        1,
        "Check",
        "manual",
        "human",
        "queued",
        "{}",
        null,
        1,
        300);
  }
}
