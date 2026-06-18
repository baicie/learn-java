package io.aegisops.runner.executor.webhook;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public record WebhookHttpRequest(
    String method, URI uri, Map<String, String> headers, String body, Duration timeout) {}
