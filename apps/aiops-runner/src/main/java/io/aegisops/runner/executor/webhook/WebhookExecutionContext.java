package io.aegisops.runner.executor.webhook;

import io.aegisops.execution.dto.WebhookConnectorRecord;
import io.aegisops.execution.dto.WebhookPolicyRecord;
import java.net.URI;
import java.util.Map;

public record WebhookExecutionContext(
    WebhookConnectorRecord connector,
    WebhookPolicyRecord policy,
    String method,
    URI uri,
    Map<String, String> headers,
    String body) {}
