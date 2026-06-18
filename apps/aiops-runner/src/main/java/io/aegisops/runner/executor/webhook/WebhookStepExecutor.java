package io.aegisops.runner.executor.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.WebhookJson;
import io.aegisops.execution.WebhookRepository;
import io.aegisops.execution.dto.ExecutionArtifactCreateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.execution.dto.WebhookConnectorRecord;
import io.aegisops.execution.dto.WebhookPolicyRecord;
import io.aegisops.runner.executor.StepExecutionContext;
import io.aegisops.runner.executor.StepExecutionResult;
import io.aegisops.runner.executor.StepExecutor;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WebhookStepExecutor implements StepExecutor {
  private final WebhookRepository repository;
  private final WebhookHttpClient httpClient;
  private final WebhookSecurityValidator validator;
  private final WebhookHeaderMasker headerMasker;
  private final WebhookJson json;
  private final ObjectMapper objectMapper;

  public WebhookStepExecutor(
      WebhookRepository repository,
      WebhookHttpClient httpClient,
      WebhookSecurityValidator validator,
      WebhookHeaderMasker headerMasker,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.httpClient = httpClient;
    this.validator = validator;
    this.headerMasker = headerMasker;
    this.objectMapper = objectMapper;
    this.json = new WebhookJson(objectMapper);
  }

  @Override
  public boolean supports(String actionType) {
    return "webhook".equals(actionType);
  }

  @Override
  public StepExecutionResult execute(StepExecutionContext context, ExecutionStepRecord step) {
    WebhookActionPayload payload =
        WebhookActionPayload.parse(objectMapper, step.actionPayloadJson());

    WebhookConnectorRecord connector =
        repository
            .findConnector(step.tenantId(), payload.connectorId())
            .orElseThrow(
                () ->
                    new AppException("WEBHOOK_CONNECTOR_NOT_FOUND", "Webhook connector not found"));

    WebhookPolicyRecord policy =
        repository.findPolicy(step.tenantId(), connector.id()).orElse(null);

    WebhookExecutionContext execCtx = buildContext(connector, policy, payload);

    if (context.dryRun()) {
      return executeDryRun(step, execCtx);
    }

    if (!context.liveEnabled()) {
      return executeLiveDisabled(step, execCtx);
    }

    return executeLive(step, execCtx);
  }

  private WebhookExecutionContext buildContext(
      WebhookConnectorRecord connector, WebhookPolicyRecord policy, WebhookActionPayload payload) {
    String method = normalizeMethod(payload.method(), connector.defaultMethod());
    URI uri = buildUri(connector.baseUrl(), payload.path());
    Map<String, String> headers = mergedHeaders(connector, payload);
    String body = payload.body() == null ? "" : payload.body();
    return new WebhookExecutionContext(connector, policy, method, uri, headers, body);
  }

  private StepExecutionResult executeDryRun(ExecutionStepRecord step, WebhookExecutionContext ctx) {
    validator.validateDryRun(ctx.connector(), ctx.policy(), ctx.method(), ctx.uri(), ctx.body());
    Map<String, Object> artifactData =
        Map.of(
            "method",
            ctx.method(),
            "url",
            ctx.uri().toString(),
            "headers",
            headerMasker.mask(ctx.connector(), ctx.headers()),
            "bodyBytes",
            ctx.body().getBytes(StandardCharsets.UTF_8).length,
            "live",
            false);
    return StepExecutionResult.success(
        "DRY-RUN webhook request: " + ctx.method() + " " + ctx.uri(),
        List.of(artifact(step, "webhook-dry-run-request.json", json.write(artifactData))));
  }

  private StepExecutionResult executeLiveDisabled(
      ExecutionStepRecord step, WebhookExecutionContext ctx) {
    Map<String, Object> artifactData = Map.of("method", ctx.method(), "url", ctx.uri().toString());
    return StepExecutionResult.failure(
        "Live webhook execution is disabled.",
        List.of(artifact(step, "webhook-live-disabled.json", json.write(artifactData))));
  }

  private StepExecutionResult executeLive(ExecutionStepRecord step, WebhookExecutionContext ctx) {
    validator.validateLive(ctx.connector(), ctx.policy(), ctx.method(), ctx.uri(), ctx.body());

    Duration timeout =
        Duration.ofMillis(ctx.policy() == null ? 5000 : ctx.policy().timeoutMillis());
    WebhookHttpResponse response =
        httpClient.send(
            new WebhookHttpRequest(ctx.method(), ctx.uri(), ctx.headers(), ctx.body(), timeout));

    Map<String, Object> artifactPayload = buildResponseArtifact(ctx, response);
    ExecutionArtifactCreateCommand artifact =
        artifact(step, "webhook-response.json", json.write(artifactPayload));

    if (response.success()) {
      return StepExecutionResult.success(
          "Webhook request succeeded with status " + response.statusCode(), List.of(artifact));
    }
    return StepExecutionResult.failure(
        "Webhook request failed with status " + response.statusCode(), List.of(artifact));
  }

  private Map<String, Object> buildResponseArtifact(
      WebhookExecutionContext ctx, WebhookHttpResponse response) {
    return Map.of(
        "request",
            Map.of(
                "method",
                ctx.method(),
                "url",
                ctx.uri().toString(),
                "headers",
                headerMasker.mask(ctx.connector(), ctx.headers()),
                "bodyBytes",
                ctx.body().getBytes(StandardCharsets.UTF_8).length),
        "response",
            Map.of(
                "statusCode",
                response.statusCode(),
                "headers",
                headerMasker.mask(ctx.connector(), response.headers()),
                "bodyPreview",
                preview(response.body(), 4096)));
  }

  private Map<String, String> mergedHeaders(
      WebhookConnectorRecord connector, WebhookActionPayload payload) {
    Map<String, String> headers = new HashMap<>(json.readStringMap(connector.defaultHeadersJson()));
    headers.putAll(payload.headers() == null ? Map.of() : payload.headers());
    return headers;
  }

  private String normalizeMethod(String method, String fallback) {
    String value = method == null || method.isBlank() ? fallback : method;
    return value.toUpperCase(java.util.Locale.ROOT);
  }

  private URI buildUri(String baseUrl, String path) {
    URI base = URI.create(baseUrl);
    if (path == null || path.isBlank()) {
      return base;
    }
    String normalizedBase =
        baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    String normalizedPath = path.startsWith("/") ? path : "/" + path;
    return URI.create(normalizedBase + normalizedPath);
  }

  private String preview(String value, int max) {
    if (value == null) {
      return "";
    }
    return value.length() <= max ? value : value.substring(0, max);
  }

  private ExecutionArtifactCreateCommand artifact(
      ExecutionStepRecord step, String name, String content) {
    return new ExecutionArtifactCreateCommand(
        newId("artifact"),
        step.tenantId(),
        step.executionId(),
        step.id(),
        "json",
        name,
        content,
        "{}");
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
