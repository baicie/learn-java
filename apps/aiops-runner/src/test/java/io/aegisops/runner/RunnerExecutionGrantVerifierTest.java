package io.aegisops.runner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.security.ExecutionGrantClaims;
import io.aegisops.common.security.ExecutionGrantCodec;
import io.aegisops.common.security.InvalidExecutionGrantException;
import io.aegisops.execution.ExecutionSnapshotHasher;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionStepRecord;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RunnerExecutionGrantVerifierTest {
  private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

  private ObjectMapper objectMapper;
  private Clock clock;
  private KeyPair current;
  private KeyPair previous;

  @BeforeEach
  void setUp() throws Exception {
    objectMapper = new ObjectMapper();
    clock = Clock.fixed(NOW, ZoneOffset.UTC);
    current = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    previous = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
  }

  @Test
  void acceptsCurrentAndPreviousSigningKeys() {
    List<ExecutionStepRecord> steps = List.of(step("{\"command\":\"echo ok\"}"));

    assertDoesNotThrow(
        () -> verifier().validate(signedRun(current, "task-grant-v2", steps), steps));
    assertDoesNotThrow(
        () -> verifier().validate(signedRun(previous, "task-grant-v1", steps), steps));
  }

  @Test
  void rejectsMissingGrantForLegacyDatabaseRows() {
    assertThrows(
        InvalidExecutionGrantException.class,
        () -> verifier().validate(unsignedRun(), List.of(step("{}"))));
  }

  @Test
  void rejectsStepSnapshotTampering() {
    List<ExecutionStepRecord> signedSteps = List.of(step("{\"command\":\"echo ok\"}"));
    ExecutionRunRecord run = signedRun(current, "task-grant-v2", signedSteps);
    List<ExecutionStepRecord> tampered = List.of(step("{\"command\":\"curl evil\"}"));

    assertThrows(InvalidExecutionGrantException.class, () -> verifier().validate(run, tampered));
  }

  @Test
  void rejectsExpiredGrant() {
    List<ExecutionStepRecord> steps = List.of(step("{}"));
    ExecutionRunRecord run = signedRun(current, "task-grant-v2", steps);

    assertThrows(
        InvalidExecutionGrantException.class,
        () -> verifierAt(NOW.plusSeconds(3601)).validate(run, steps));
  }

  @Test
  void rejectsWrongIssuerAudienceAndScope() {
    List<ExecutionStepRecord> steps = List.of(step("{}"));

    assertThrows(
        InvalidExecutionGrantException.class,
        () ->
            verifier()
                .validate(
                    signedRun(
                        unsignedRun(),
                        current,
                        "task-grant-v2",
                        steps,
                        new GrantProfile(
                            "another-app",
                            Set.of("aiops-runner"),
                            List.of("runbook:execute"),
                            NOW.plusSeconds(3600))),
                    steps));
    assertThrows(
        InvalidExecutionGrantException.class,
        () ->
            verifier()
                .validate(
                    signedRun(
                        unsignedRun(),
                        current,
                        "task-grant-v2",
                        steps,
                        new GrantProfile(
                            "aegisops-app",
                            Set.of("another-runner"),
                            List.of("runbook:execute"),
                            NOW.plusSeconds(3600))),
                    steps));
    assertThrows(
        InvalidExecutionGrantException.class,
        () ->
            verifier()
                .validate(
                    signedRun(
                        unsignedRun(),
                        current,
                        "task-grant-v2",
                        steps,
                        new GrantProfile(
                            "aegisops-app",
                            Set.of("aiops-runner"),
                            List.of("evidence:read"),
                            NOW.plusSeconds(3600))),
                    steps));
  }

  @Test
  void rejectsGrantWhenQueueWaitLeavesLessThanMaximumExecutionDuration() {
    List<ExecutionStepRecord> steps = List.of(step("{}"));
    ExecutionRunRecord claimedTooLate = run("normal", null, null, null, NOW.plusSeconds(1801));
    ExecutionRunRecord run =
        signedRun(claimedTooLate, current, "task-grant-v2", steps, defaultProfile());

    assertThrows(
        InvalidExecutionGrantException.class,
        () -> verifierAt(NOW.plusSeconds(1801)).validate(run, steps));
  }

  @Test
  void acceptsRetryAndRollbackBindings() {
    List<ExecutionStepRecord> steps = List.of(step("{}"));
    ExecutionRunRecord retry = run("normal", "exec_previous", null, null, NOW);
    ExecutionRunRecord rollback = run("rollback", null, "rbp_1", "exec_source", NOW);

    assertDoesNotThrow(
        () -> verifier().validate(signedRun(retry, current, "task-grant-v2", steps), steps));
    assertDoesNotThrow(
        () -> verifier().validate(signedRun(rollback, current, "task-grant-v2", steps), steps));
  }

  @Test
  void rejectsSignedLiveRunWhenApprovalSnapshotIsNotApproved() {
    List<ExecutionStepRecord> steps = List.of(step("{}"));
    ExecutionRunRecord liveRun =
        liveRun(
            "approval_1",
            """
            {
              "approvalId": "approval_1",
              "planId": "plan_1",
              "status": "pending",
              "requiredApprovals": 1,
              "approvedCount": 0
            }
            """);
    ExecutionRunRecord signed = signedRun(liveRun, current, "task-grant-v2", steps);

    assertThrows(InvalidExecutionGrantException.class, () -> verifier().validate(signed, steps));
  }

  private RunnerExecutionGrantVerifier verifier() {
    return verifierAt(NOW);
  }

  private RunnerExecutionGrantVerifier verifierAt(Instant now) {
    RunnerExecutionGrantProperties properties = new RunnerExecutionGrantProperties();
    properties.setCurrentKeyId("task-grant-v2");
    properties.setPreviousKeyId("task-grant-v1");
    properties.setPreviousPublicKeyFile("unused-in-unit-test");
    return new RunnerExecutionGrantVerifier(
        properties,
        new ExecutionGrantCodec(objectMapper, Clock.fixed(now, ZoneOffset.UTC)),
        new ExecutionSnapshotHasher(objectMapper),
        Map.of(
            "task-grant-v2", current.getPublic(),
            "task-grant-v1", previous.getPublic()));
  }

  private ExecutionRunRecord signedRun(KeyPair keys, String kid, List<ExecutionStepRecord> steps) {
    return signedRun(unsignedRun(), keys, kid, steps);
  }

  private ExecutionRunRecord signedRun(
      ExecutionRunRecord unsigned, KeyPair keys, String kid, List<ExecutionStepRecord> steps) {
    return signedRun(unsigned, keys, kid, steps, defaultProfile());
  }

  private ExecutionRunRecord signedRun(
      ExecutionRunRecord unsigned,
      KeyPair keys,
      String kid,
      List<ExecutionStepRecord> steps,
      GrantProfile profile) {
    String snapshot = new ExecutionSnapshotHasher(objectMapper).sha256(unsigned, steps);
    String token =
        new ExecutionGrantCodec(objectMapper, clock)
            .issue(
                keys.getPrivate(),
                kid,
                new ExecutionGrantClaims(
                    profile.issuer(),
                    "execution:" + unsigned.id(),
                    profile.audiences(),
                    profile.scopes(),
                    unsigned.tenantId(),
                    unsigned.incidentId(),
                    unsigned.id(),
                    unsigned.planId(),
                    unsigned.mode(),
                    unsigned.executionKind(),
                    unsigned.rollbackPlanId(),
                    unsigned.rollbackOfExecutionId(),
                    snapshot,
                    unsigned.timeoutSeconds(),
                    NOW,
                    profile.expiresAt(),
                    "grant_1"));
    return withGrant(unsigned, token, snapshot, profile.expiresAt());
  }

  private GrantProfile defaultProfile() {
    return new GrantProfile(
        "aegisops-app", Set.of("aiops-runner"), List.of("runbook:execute"), NOW.plusSeconds(3600));
  }

  private record GrantProfile(
      String issuer, Set<String> audiences, List<String> scopes, Instant expiresAt) {}

  private ExecutionRunRecord unsignedRun() {
    return run("normal", null, null, null, NOW);
  }

  private ExecutionRunRecord liveRun(String approvalId, String approvalSnapshotJson) {
    ExecutionRunRecord run = unsignedRun();
    return new ExecutionRunRecord(
        run.id(),
        run.tenantId(),
        run.incidentId(),
        run.planId(),
        run.status(),
        "live",
        run.requestedBy(),
        run.runnerId(),
        run.startedAt(),
        run.finishedAt(),
        run.errorMessage(),
        run.summary(),
        run.attempt(),
        run.maxAttempts(),
        run.retryOfExecutionId(),
        run.leaseUntil(),
        run.heartbeatAt(),
        run.timeoutSeconds(),
        approvalId,
        approvalSnapshotJson,
        run.planRiskLevel(),
        run.liveGuardPassedAt(),
        run.executionKind(),
        run.rollbackPlanId(),
        run.rollbackOfExecutionId(),
        run.executionGrant(),
        run.executionSnapshotSha256(),
        run.executionGrantExpiresAt(),
        run.createdAt(),
        run.updatedAt());
  }

  private ExecutionRunRecord run(
      String executionKind,
      String retryOfExecutionId,
      String rollbackPlanId,
      String rollbackOfExecutionId,
      Instant startedAt) {
    return new ExecutionRunRecord(
        "exec_1",
        "tenant_1",
        "inc_1",
        "plan_1",
        "running",
        "dry_run",
        "alice",
        "runner_1",
        OffsetDateTime.ofInstant(startedAt, ZoneOffset.UTC),
        null,
        null,
        null,
        1,
        3,
        retryOfExecutionId,
        OffsetDateTime.ofInstant(NOW.plusSeconds(60), ZoneOffset.UTC),
        OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
        1800,
        null,
        "{}",
        "medium",
        null,
        executionKind,
        rollbackPlanId,
        rollbackOfExecutionId,
        null,
        null,
        null,
        OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
        OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
  }

  private ExecutionRunRecord withGrant(
      ExecutionRunRecord run, String token, String snapshot, Instant expiresAt) {
    return new ExecutionRunRecord(
        run.id(),
        run.tenantId(),
        run.incidentId(),
        run.planId(),
        run.status(),
        run.mode(),
        run.requestedBy(),
        run.runnerId(),
        run.startedAt(),
        run.finishedAt(),
        run.errorMessage(),
        run.summary(),
        run.attempt(),
        run.maxAttempts(),
        run.retryOfExecutionId(),
        run.leaseUntil(),
        run.heartbeatAt(),
        run.timeoutSeconds(),
        run.approvalId(),
        run.approvalSnapshotJson(),
        run.planRiskLevel(),
        run.liveGuardPassedAt(),
        run.executionKind(),
        run.rollbackPlanId(),
        run.rollbackOfExecutionId(),
        token,
        snapshot,
        OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC),
        run.createdAt(),
        run.updatedAt());
  }

  private ExecutionStepRecord step(String payload) {
    return new ExecutionStepRecord(
        "step_1",
        "tenant_1",
        "exec_1",
        "planstep_1",
        1,
        "Check",
        "shell",
        "host",
        "queued",
        payload,
        "echo ok",
        null,
        null,
        null,
        null,
        1,
        300,
        0,
        OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
        OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
  }
}
