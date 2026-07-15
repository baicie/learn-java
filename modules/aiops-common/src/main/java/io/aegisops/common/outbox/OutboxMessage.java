package io.aegisops.common.outbox;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

public record OutboxMessage(
    String tenantId,
    String targetApp,
    String jobName,
    Map<String, Object> payload,
    String idempotencyKey,
    int maxRetries,
    OffsetDateTime availableAt) {

  public OutboxMessage {
    targetApp = requireText(targetApp, "targetApp", 32);
    jobName = requireText(jobName, "jobName", 64);
    tenantId = blankToNull(tenantId);
    idempotencyKey = blankToNull(idempotencyKey);
    if (idempotencyKey != null && idempotencyKey.length() > 128) {
      throw new IllegalArgumentException("idempotencyKey must not exceed 128 characters");
    }
    payload = payload == null ? Map.of() : Map.copyOf(payload);
    maxRetries = Math.max(1, maxRetries);
    availableAt = availableAt == null ? OffsetDateTime.now(ZoneOffset.UTC) : availableAt;
  }

  private static String requireText(String value, String field, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    String normalized = value.trim();
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
    }
    return normalized;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
