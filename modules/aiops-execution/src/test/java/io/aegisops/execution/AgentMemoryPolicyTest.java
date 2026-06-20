package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.AgentMemoryCreateRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentMemoryPolicyTest {
  private final AgentMemoryPolicy policy = new AgentMemoryPolicy();

  @Test
  void allowSafeMemory() {
    assertDoesNotThrow(
        () ->
            policy.validateCreate(
                new AgentMemoryCreateRequest(
                    "tenant_1",
                    "tenant",
                    null,
                    "root_cause_pattern",
                    "diagnosis",
                    "inc_1",
                    "Redis timeout pattern",
                    "Order service has recurring redis timeout pattern.",
                    List.of("redis", "timeout"),
                    0.8,
                    null,
                    "agent")));
  }

  @Test
  void rejectSecretLikeMemory() {
    assertThrows(
        AppException.class,
        () ->
            policy.validateCreate(
                new AgentMemoryCreateRequest(
                    "tenant_1",
                    "tenant",
                    null,
                    "root_cause_pattern",
                    "diagnosis",
                    "inc_1",
                    "Secret",
                    "password=123456",
                    List.of(),
                    0.8,
                    null,
                    "agent")));
  }

  @Test
  void rejectInvalidMemoryType() {
    assertThrows(
        AppException.class,
        () ->
            policy.validateCreate(
                new AgentMemoryCreateRequest(
                    "tenant_1",
                    "tenant",
                    null,
                    "free_form",
                    "diagnosis",
                    "inc_1",
                    "Invalid",
                    "content",
                    List.of(),
                    0.8,
                    null,
                    "agent")));
  }
}
