package io.aegisops.runner.executor.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.WebhookJson;
import io.aegisops.execution.WebhookRepository;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.execution.dto.WebhookConnectorCreateCommand;
import io.aegisops.execution.dto.WebhookConnectorRecord;
import io.aegisops.execution.dto.WebhookPolicyCreateCommand;
import io.aegisops.execution.dto.WebhookPolicyRecord;
import io.aegisops.runner.executor.StepExecutionContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WebhookStepExecutorTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final WebhookJson json = new WebhookJson(objectMapper);

  @Test
  void dryRunDoesNotSendHttpRequest() {
    FakeWebhookRepository repository = new FakeWebhookRepository();
    FakeWebhookHttpClient httpClient = new FakeWebhookHttpClient();

    WebhookStepExecutor executor = executor(repository, httpClient);

    var result = executor.execute(context("dry_run", false), step(payload("whc_1")));

    assertTrue(result.success());
    assertFalse(httpClient.called);
    assertEquals(1, result.artifacts().size());
    assertEquals("webhook-dry-run-request.json", result.artifacts().get(0).name());
  }

  @Test
  void liveDisabledFailsWithoutHttpRequest() {
    FakeWebhookRepository repository = new FakeWebhookRepository();
    FakeWebhookHttpClient httpClient = new FakeWebhookHttpClient();

    WebhookStepExecutor executor = executor(repository, httpClient);

    var result = executor.execute(context("live", false), step(payload("whc_1")));

    assertFalse(result.success());
    assertFalse(httpClient.called);
    assertEquals("Live webhook execution is disabled.", result.errorMessage());
  }

  @Test
  void liveAllowedSendsRequestAndWritesResponseArtifact() {
    FakeWebhookRepository repository = new FakeWebhookRepository();
    FakeWebhookHttpClient httpClient = new FakeWebhookHttpClient();
    httpClient.response = new WebhookHttpResponse(200, Map.of("x-ok", "true"), "{\"ok\":true}");

    WebhookStepExecutor executor = executor(repository, httpClient);

    var result = executor.execute(context("live", true), step(payload("whc_1")));

    assertTrue(result.success());
    assertTrue(httpClient.called);
    assertEquals("POST", httpClient.request.method());
    assertEquals("https://ops.example.com/internal/restart", httpClient.request.uri().toString());
    assertEquals(1, result.artifacts().size());
    assertEquals("webhook-response.json", result.artifacts().get(0).name());
  }

  private WebhookStepExecutor executor(
      FakeWebhookRepository repository, FakeWebhookHttpClient httpClient) {
    return new WebhookStepExecutor(
        repository,
        httpClient,
        new WebhookSecurityValidator(
            objectMapper,
            host -> {
              try {
                return List.of(java.net.InetAddress.getByName("93.184.216.34"));
              } catch (Exception ex) {
                throw new IllegalStateException(ex);
              }
            }),
        new WebhookHeaderMasker(objectMapper),
        objectMapper);
  }

  private StepExecutionContext context(String mode, boolean liveEnabled) {
    return new StepExecutionContext(
        new ExecutionRunRecord(
            "exec_1",
            "tenant_1",
            "inc_1",
            "plan_1",
            "running",
            mode,
            "alice",
            "runner_1",
            OffsetDateTime.now(),
            null,
            null,
            null,
            1,
            1,
            null,
            OffsetDateTime.now().plusSeconds(60),
            OffsetDateTime.now(),
            1800,
            null,
            null,
            null,
            null,
            "normal",
            null,
            null,
            OffsetDateTime.now(),
            OffsetDateTime.now()),
        liveEnabled);
  }

  private ExecutionStepRecord step(String payload) {
    return new ExecutionStepRecord(
        "step_1",
        "tenant_1",
        "exec_1",
        "planstep_1",
        1,
        "Restart service",
        "webhook",
        "service",
        "queued",
        payload,
        "",
        null,
        null,
        null,
        null,
        1,
        300,
        0,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private String payload(String connectorId) {
    return json.write(
        Map.of(
            "connectorId",
            connectorId,
            "method",
            "POST",
            "path",
            "/internal/restart",
            "headers",
            Map.of("X-Source", "aegisops"),
            "body",
            Map.of("serviceName", "order-service")));
  }

  private class FakeWebhookRepository implements WebhookRepository {
    @Override
    public Optional<WebhookConnectorRecord> findConnector(String tenantId, String connectorId) {
      return Optional.of(
          new WebhookConnectorRecord(
              "whc_1",
              tenantId,
              "ops",
              "desc",
              "https://ops.example.com",
              "POST",
              json.write(Map.of("Authorization", "secret")),
              json.write(List.of("authorization")),
              true,
              "alice",
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public Optional<WebhookPolicyRecord> findPolicy(String tenantId, String connectorId) {
      return Optional.of(
          new WebhookPolicyRecord(
              "whp_1",
              tenantId,
              connectorId,
              true,
              json.write(List.of("ops.example.com")),
              json.write(List.of("POST")),
              true,
              true,
              true,
              32768,
              5000,
              true,
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public void createConnector(WebhookConnectorCreateCommand command) {}

    @Override
    public void createPolicy(WebhookPolicyCreateCommand command) {}

    @Override
    public List<WebhookConnectorRecord> listConnectors(String tenantId, boolean includeDisabled) {
      return List.of();
    }

    @Override
    public boolean setConnectorEnabled(String tenantId, String connectorId, boolean enabled) {
      return true;
    }
  }

  private static class FakeWebhookHttpClient implements WebhookHttpClient {
    boolean called;
    WebhookHttpRequest request;
    WebhookHttpResponse response = new WebhookHttpResponse(200, Map.of(), "ok");

    @Override
    public WebhookHttpResponse send(WebhookHttpRequest request) {
      this.called = true;
      this.request = request;
      return response;
    }
  }
}
