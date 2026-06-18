package io.aegisops.runner.executor.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.WebhookJson;
import io.aegisops.execution.dto.WebhookConnectorRecord;
import io.aegisops.execution.dto.WebhookPolicyRecord;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class WebhookSecurityValidator {
  private final WebhookJson json;
  private final WebhookAddressResolver addressResolver;

  public WebhookSecurityValidator(
      ObjectMapper objectMapper, WebhookAddressResolver addressResolver) {
    this.json = new WebhookJson(objectMapper);
    this.addressResolver = addressResolver;
  }

  public void validateLive(
      WebhookConnectorRecord connector,
      WebhookPolicyRecord policy,
      String method,
      URI uri,
      String body) {
    if (!connector.enabled()) {
      throw new AppException("WEBHOOK_CONNECTOR_DISABLED", "Webhook connector is disabled");
    }

    if (policy == null || !policy.enabled()) {
      throw new AppException("WEBHOOK_POLICY_DISABLED", "Webhook policy is disabled");
    }

    if (!policy.allowLive()) {
      throw new AppException(
          "WEBHOOK_LIVE_NOT_ALLOWED", "Webhook live execution is not allowed by policy");
    }

    validateScheme(uri);
    validateMethod(policy, method);
    validateBody(policy, body);
    validateHost(policy, uri);
  }

  public void validateDryRun(
      WebhookConnectorRecord connector,
      WebhookPolicyRecord policy,
      String method,
      URI uri,
      String body) {
    if (!connector.enabled()) {
      throw new AppException("WEBHOOK_CONNECTOR_DISABLED", "Webhook connector is disabled");
    }

    validateScheme(uri);

    if (policy != null && policy.enabled()) {
      validateMethod(policy, method);
      validateBody(policy, body);
      validateHost(policy, uri);
    }
  }

  private void validateScheme(URI uri) {
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!List.of("http", "https").contains(scheme)) {
      throw new AppException("WEBHOOK_SCHEME_INVALID", "Webhook scheme must be http or https");
    }

    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw new AppException("WEBHOOK_HOST_REQUIRED", "Webhook host is required");
    }

    if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
      throw new AppException("WEBHOOK_USERINFO_BLOCKED", "Webhook URL userinfo is not allowed");
    }
  }

  private void validateMethod(WebhookPolicyRecord policy, String method) {
    List<String> allowed =
        json.readStringList(policy.allowedMethodsJson()).stream()
            .map(item -> item.toUpperCase(Locale.ROOT))
            .toList();

    if (!allowed.contains(method.toUpperCase(Locale.ROOT))) {
      throw new AppException("WEBHOOK_METHOD_NOT_ALLOWED", "Webhook method is not allowed");
    }
  }

  private void validateBody(WebhookPolicyRecord policy, String body) {
    int size = body == null ? 0 : body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    if (size > policy.maxBodyBytes()) {
      throw new AppException("WEBHOOK_BODY_TOO_LARGE", "Webhook body exceeds policy limit");
    }
  }

  private void validateHost(WebhookPolicyRecord policy, URI uri) {
    String host = uri.getHost().toLowerCase(Locale.ROOT);

    List<String> allowedHosts =
        json.readStringList(policy.allowedHostsJson()).stream()
            .map(item -> item.toLowerCase(Locale.ROOT))
            .toList();

    if (!allowedHosts.contains(host)) {
      throw new AppException("WEBHOOK_HOST_NOT_ALLOWED", "Webhook host is not allowed");
    }

    if (policy.blockLocalhost() && isLocalhost(host)) {
      throw new AppException("WEBHOOK_LOCALHOST_BLOCKED", "Webhook localhost target is blocked");
    }

    List<InetAddress> addresses = addressResolver.resolveAll(host);
    for (InetAddress address : addresses) {
      validateAddress(policy, address);
    }
  }

  private void validateAddress(WebhookPolicyRecord policy, InetAddress address) {
    String hostAddress = address.getHostAddress();

    if (policy.blockMetadataIp() && isMetadataIp(hostAddress)) {
      throw new AppException(
          "WEBHOOK_METADATA_IP_BLOCKED", "Webhook metadata IP target is blocked");
    }

    if (policy.blockPrivateIp() && isPrivateOrLoopback(address)) {
      throw new AppException("WEBHOOK_PRIVATE_IP_BLOCKED", "Webhook private IP target is blocked");
    }
  }

  private boolean isLocalhost(String host) {
    return "localhost".equals(host) || host.endsWith(".localhost");
  }

  private boolean isMetadataIp(String hostAddress) {
    return "169.254.169.254".equals(hostAddress)
        || "0:0:0:0:0:ffff:a9fe:a9fe".equalsIgnoreCase(hostAddress);
  }

  private boolean isPrivateOrLoopback(InetAddress address) {
    return address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isSiteLocalAddress()
        || address.isLinkLocalAddress();
  }
}
