package io.aegisops.worker.outbox;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the worker-side {@code OutboxPoller}.
 *
 * <p>Backed by the {@code aiops.outbox.*} prefix in {@code application.yml}. The {@code targetApp}
 * value MUST match the {@code target_app} column on rows the worker is expected to pick (default
 * {@code "worker"}; the {@code runner} uses {@code "runner"} and the {@code server} uses {@code
 * "server"}). Spring routes properties by the exact bean name, so each app's {@code OutboxPoller}
 * consumes its own slice of the table without overlapping.
 */
@ConfigurationProperties(prefix = "aiops.outbox")
public record OutboxProperties(
    boolean enabled, @Min(100) long pollDelayMs, @Min(1) int batchSize, String targetApp) {

  public OutboxProperties {
    if (targetApp == null || targetApp.isBlank()) {
      targetApp = "worker";
    }
  }
}
