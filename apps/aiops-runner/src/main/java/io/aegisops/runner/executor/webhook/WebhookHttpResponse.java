package io.aegisops.runner.executor.webhook;

import java.util.Map;

public record WebhookHttpResponse(int statusCode, Map<String, String> headers, String body) {
  public boolean success() {
    return statusCode >= 200 && statusCode < 300;
  }
}
