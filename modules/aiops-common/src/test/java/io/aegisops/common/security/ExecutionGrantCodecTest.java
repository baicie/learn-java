package io.aegisops.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExecutionGrantCodecTest {
  private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

  private KeyPair current;
  private KeyPair previous;
  private ExecutionGrantCodec codec;

  @BeforeEach
  void setUp() throws Exception {
    current = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    previous = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    codec = new ExecutionGrantCodec(new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void signsAndVerifiesExecutionBoundClaims() {
    ExecutionGrantClaims expected = claims(NOW.plusSeconds(1800));

    String token = codec.issue(current.getPrivate(), "task-grant-v2", expected);

    ExecutionGrantClaims actual =
        codec.verify(Map.of("task-grant-v2", current.getPublic()), token, "aiops-runner");
    assertEquals(expected, actual);
  }

  @Test
  void acceptsPreviousVerificationKeyDuringRotation() {
    String token =
        codec.issue(previous.getPrivate(), "task-grant-v1", claims(NOW.plusSeconds(1800)));

    ExecutionGrantClaims actual =
        codec.verify(
            Map.of(
                "task-grant-v2", current.getPublic(),
                "task-grant-v1", previous.getPublic()),
            token,
            "aiops-runner");

    assertEquals("exec_1", actual.executionId());
  }

  @Test
  void allowsQueueWaitWindowBeyondMaximumExecutionDuration() {
    ExecutionGrantClaims queued = claims(NOW.plusSeconds(3600));

    String token = codec.issue(current.getPrivate(), "task-grant-v2", queued);

    ExecutionGrantClaims actual =
        codec.verify(Map.of("task-grant-v2", current.getPublic()), token, "aiops-runner");
    assertEquals(1800, actual.maxDurationSeconds());
    assertEquals(NOW.plusSeconds(3600), actual.expiresAt());
  }

  @Test
  void rejectsExpiredGrant() {
    String token =
        codec.issue(current.getPrivate(), "task-grant-v2", claims(NOW.plusSeconds(1800)));
    ExecutionGrantCodec expiredCodec =
        new ExecutionGrantCodec(
            new ObjectMapper(), Clock.fixed(NOW.plusSeconds(1801), ZoneOffset.UTC));

    assertThrows(
        InvalidExecutionGrantException.class,
        () ->
            expiredCodec.verify(
                Map.of("task-grant-v2", current.getPublic()), token, "aiops-runner"));
  }

  private ExecutionGrantClaims claims(Instant expiresAt) {
    return new ExecutionGrantClaims(
        "aegisops-app",
        "execution:exec_1",
        Set.of("aiops-runner"),
        List.of("runbook:execute"),
        "tenant_1",
        "inc_1",
        "exec_1",
        "plan_1",
        "dry_run",
        "normal",
        null,
        null,
        "a3c4f3071b8fd3311d6c9e826dd7f8efb1a0d9d321b444a9a3831733f81035ac",
        1800,
        NOW,
        expiresAt,
        "grant_1");
  }
}
