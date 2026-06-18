package io.aegisops.runner.executor.webhook;

public interface WebhookHttpClient {
  WebhookHttpResponse send(WebhookHttpRequest request);
}
