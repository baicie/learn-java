package io.aegisops.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.WebhookConnectorCreateCommand;
import io.aegisops.execution.dto.WebhookConnectorCreateRequest;
import io.aegisops.execution.dto.WebhookConnectorRecord;
import io.aegisops.execution.dto.WebhookConnectorResponse;
import io.aegisops.execution.dto.WebhookPolicyCreateCommand;
import io.aegisops.execution.dto.WebhookPolicyRecord;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookConnectorService {
  private final WebhookRepository repository;
  private final WebhookJson json;

  public WebhookConnectorService(WebhookRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.json = new WebhookJson(objectMapper);
  }

  public List<WebhookConnectorResponse> list(String tenantId, boolean includeDisabled) {
    return repository.listConnectors(tenantId, includeDisabled).stream()
        .map(
            record -> toResponse(record, repository.findPolicy(tenantId, record.id()).orElse(null)))
        .toList();
  }

  public WebhookConnectorResponse get(String tenantId, String connectorId) {
    WebhookConnectorRecord connector =
        repository
            .findConnector(tenantId, connectorId)
            .orElseThrow(
                () ->
                    new AppException("WEBHOOK_CONNECTOR_NOT_FOUND", "Webhook connector not found"));

    return toResponse(connector, repository.findPolicy(tenantId, connector.id()).orElse(null));
  }

  @Transactional
  public WebhookConnectorResponse create(String tenantId, WebhookConnectorCreateRequest request) {
    validateCreateRequest(request);

    String connectorId = newId("whc");
    String policyId = newId("whp");

    String method = normalizeMethod(request.defaultMethod(), "POST");
    List<String> allowedMethods =
        normalizeMethods(
            request.allowedMethods() == null ? List.of(method) : request.allowedMethods());

    String host = hostOf(request.baseUrl());
    List<String> allowedHosts =
        request.allowedHosts() == null || request.allowedHosts().isEmpty()
            ? List.of(host)
            : request.allowedHosts();

    allowedHosts.forEach(host -> rejectDangerousConfiguredHost(host));

    repository.createConnector(
        new WebhookConnectorCreateCommand(
            connectorId,
            tenantId,
            request.name().trim(),
            request.description(),
            request.baseUrl().trim(),
            method,
            json.write(request.defaultHeaders() == null ? Map.of() : request.defaultHeaders()),
            json.write(
                request.sensitiveHeaders() == null
                    ? List.of("authorization", "x-api-key", "x-token", "cookie")
                    : request.sensitiveHeaders()),
            true,
            blankToDefault(request.createdBy(), "system")));

    repository.createPolicy(
        new WebhookPolicyCreateCommand(
            policyId,
            tenantId,
            connectorId,
            Boolean.TRUE.equals(request.allowLive()),
            json.write(allowedHosts),
            json.write(allowedMethods),
            true,
            true,
            true,
            normalizeMaxBodyBytes(request.maxBodyBytes()),
            normalizeTimeoutMillis(request.timeoutMillis()),
            true));

    return get(tenantId, connectorId);
  }

  @Transactional
  public WebhookConnectorResponse setEnabled(String tenantId, String connectorId, boolean enabled) {
    boolean updated = repository.setConnectorEnabled(tenantId, connectorId, enabled);
    if (!updated) {
      throw new AppException(
          "WEBHOOK_CONNECTOR_UPDATE_FAILED", "Webhook connector was not updated");
    }
    return get(tenantId, connectorId);
  }

  private void validateCreateRequest(WebhookConnectorCreateRequest request) {
    if (request == null) {
      throw new AppException(
          "WEBHOOK_CONNECTOR_REQUEST_REQUIRED", "Webhook connector request is required");
    }

    if (request.name() == null || request.name().isBlank()) {
      throw new AppException(
          "WEBHOOK_CONNECTOR_NAME_REQUIRED", "Webhook connector name is required");
    }

    if (request.baseUrl() == null || request.baseUrl().isBlank()) {
      throw new AppException(
          "WEBHOOK_CONNECTOR_BASE_URL_REQUIRED", "Webhook connector baseUrl is required");
    }

    URI uri = URI.create(request.baseUrl().trim());
    if (!List.of("http", "https").contains(uri.getScheme())) {
      throw new AppException(
          "WEBHOOK_CONNECTOR_SCHEME_INVALID", "Webhook connector scheme must be http or https");
    }

    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw new AppException(
          "WEBHOOK_CONNECTOR_HOST_REQUIRED", "Webhook connector host is required");
    }

    rejectDangerousConfiguredHost(uri.getHost());
  }

  private WebhookConnectorResponse toResponse(
      WebhookConnectorRecord connector, WebhookPolicyRecord policy) {
    return new WebhookConnectorResponse(
        connector.id(),
        connector.name(),
        connector.description(),
        connector.baseUrl(),
        connector.defaultMethod(),
        json.readStringMap(connector.defaultHeadersJson()),
        json.readStringList(connector.sensitiveHeadersJson()),
        connector.enabled(),
        policy != null && policy.allowLive(),
        policy == null ? List.of() : json.readStringList(policy.allowedHostsJson()),
        policy == null
            ? List.of(connector.defaultMethod())
            : json.readStringList(policy.allowedMethodsJson()),
        policy == null || policy.blockPrivateIp(),
        policy == null || policy.blockLocalhost(),
        policy == null || policy.blockMetadataIp(),
        policy == null ? 32768 : policy.maxBodyBytes(),
        policy == null ? 5000 : policy.timeoutMillis(),
        connector.createdBy(),
        connector.createdAt(),
        connector.updatedAt());
  }

  private String hostOf(String url) {
    return URI.create(url.trim()).getHost().toLowerCase();
  }

  private void rejectDangerousConfiguredHost(String host) {
    if (host == null || host.isBlank()) {
      throw new AppException("WEBHOOK_HOST_REQUIRED", "Webhook host is required");
    }

    String normalized = host.trim().toLowerCase();

    if ("localhost".equals(normalized) || normalized.endsWith(".localhost")) {
      throw new AppException("WEBHOOK_LOCALHOST_BLOCKED", "Webhook localhost target is blocked");
    }

    if ("169.254.169.254".equals(normalized)) {
      throw new AppException(
          "WEBHOOK_METADATA_IP_BLOCKED", "Webhook metadata IP target is blocked");
    }

    if (normalized.startsWith("127.")
        || normalized.startsWith("10.")
        || normalized.startsWith("192.168.")
        || isPrivate172(normalized)
        || "::1".equals(normalized)
        || "0:0:0:0:0:0:0:1".equals(normalized)) {
      throw new AppException("WEBHOOK_PRIVATE_IP_BLOCKED", "Webhook private IP target is blocked");
    }
  }

  private boolean isPrivate172(String host) {
    if (!host.startsWith("172.")) {
      return false;
    }

    String[] parts = host.split("\\.");
    if (parts.length < 2) {
      return false;
    }

    try {
      int second = Integer.parseInt(parts[1]);
      return second >= 16 && second <= 31;
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  private String normalizeMethod(String method, String fallback) {
    String value = method == null || method.isBlank() ? fallback : method.trim().toUpperCase();
    if (!List.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(value)) {
      throw new AppException("WEBHOOK_METHOD_INVALID", "Unsupported webhook method: " + value);
    }
    return value;
  }

  private List<String> normalizeMethods(List<String> methods) {
    return methods.stream().map(item -> normalizeMethod(item, "POST")).distinct().toList();
  }

  private int normalizeMaxBodyBytes(Integer value) {
    if (value == null) {
      return 32768;
    }
    return Math.max(0, Math.min(value, 1048576));
  }

  private int normalizeTimeoutMillis(Integer value) {
    if (value == null) {
      return 5000;
    }
    return Math.max(100, Math.min(value, 60000));
  }

  private String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
