package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.WebhookConnectorCreateCommand;
import io.aegisops.execution.dto.WebhookConnectorCreateRequest;
import io.aegisops.execution.dto.WebhookConnectorRecord;
import io.aegisops.execution.dto.WebhookPolicyCreateCommand;
import io.aegisops.execution.dto.WebhookPolicyRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WebhookConnectorServiceTest {
  @Test
  void createConnectorCreatesPolicyWithDefaultHost() {
    FakeWebhookRepository repository = new FakeWebhookRepository();
    WebhookConnectorService service = new WebhookConnectorService(repository, new ObjectMapper());

    var response =
        service.create(
            "tenant_1",
            new WebhookConnectorCreateRequest(
                "ops",
                "desc",
                "https://ops.example.com",
                "POST",
                Map.of("X-Source", "aegisops"),
                List.of("authorization"),
                null,
                List.of("POST"),
                false,
                32768,
                5000,
                "alice"));

    assertEquals("ops", response.name());
    assertEquals(List.of("ops.example.com"), response.allowedHosts());
    assertEquals(List.of("POST"), response.allowedMethods());
  }

  @Test
  void createRejectsInvalidScheme() {
    WebhookConnectorService service =
        new WebhookConnectorService(new FakeWebhookRepository(), new ObjectMapper());

    assertThrows(
        AppException.class,
        () ->
            service.create(
                "tenant_1",
                new WebhookConnectorCreateRequest(
                    "bad",
                    "desc",
                    "file:///etc/passwd",
                    "POST",
                    Map.of(),
                    List.of(),
                    null,
                    List.of("POST"),
                    false,
                    32768,
                    5000,
                    "alice")));
  }

  private static class FakeWebhookRepository implements WebhookRepository {
    private final WebhookJson json = new WebhookJson(new ObjectMapper());
    WebhookConnectorCreateCommand connector;
    WebhookPolicyCreateCommand policy;

    @Override
    public void createConnector(WebhookConnectorCreateCommand command) {
      connector = command;
    }

    @Override
    public void createPolicy(WebhookPolicyCreateCommand command) {
      policy = command;
    }

    @Override
    public List<WebhookConnectorRecord> listConnectors(String tenantId, boolean includeDisabled) {
      return List.of();
    }

    @Override
    public Optional<WebhookConnectorRecord> findConnector(String tenantId, String connectorId) {
      return Optional.of(
          new WebhookConnectorRecord(
              connector.id(),
              connector.tenantId(),
              connector.name(),
              connector.description(),
              connector.baseUrl(),
              connector.defaultMethod(),
              connector.defaultHeadersJson(),
              connector.sensitiveHeadersJson(),
              connector.enabled(),
              connector.createdBy(),
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public Optional<WebhookPolicyRecord> findPolicy(String tenantId, String connectorId) {
      return Optional.of(
          new WebhookPolicyRecord(
              policy.id(),
              policy.tenantId(),
              policy.connectorId(),
              policy.allowLive(),
              policy.allowedHostsJson(),
              policy.allowedMethodsJson(),
              policy.blockPrivateIp(),
              policy.blockLocalhost(),
              policy.blockMetadataIp(),
              policy.maxBodyBytes(),
              policy.timeoutMillis(),
              policy.enabled(),
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public boolean setConnectorEnabled(String tenantId, String connectorId, boolean enabled) {
      return true;
    }
  }
}
