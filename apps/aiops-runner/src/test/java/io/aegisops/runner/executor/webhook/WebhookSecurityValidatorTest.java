package io.aegisops.runner.executor.webhook;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.WebhookJson;
import io.aegisops.execution.dto.WebhookConnectorRecord;
import io.aegisops.execution.dto.WebhookPolicyRecord;
import java.net.InetAddress;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebhookSecurityValidatorTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final WebhookJson json = new WebhookJson(objectMapper);

  @Test
  void allowAllowedPublicHost() {
    WebhookSecurityValidator validator =
        new WebhookSecurityValidator(objectMapper, host -> List.of(address("93.184.216.34")));

    assertDoesNotThrow(
        () ->
            validator.validateLive(
                connector("https://ops.example.com"),
                policy(true, List.of("ops.example.com"), List.of("POST")),
                "POST",
                URI.create("https://ops.example.com/internal/restart"),
                "{}"));
  }

  @Test
  void blockNonAllowlistedHost() {
    WebhookSecurityValidator validator =
        new WebhookSecurityValidator(objectMapper, host -> List.of(address("93.184.216.34")));

    assertThrows(
        AppException.class,
        () ->
            validator.validateLive(
                connector("https://ops.example.com"),
                policy(true, List.of("ops.example.com"), List.of("POST")),
                "POST",
                URI.create("https://evil.example.com/internal/restart"),
                "{}"));
  }

  @Test
  void blockLocalhost() {
    WebhookSecurityValidator validator =
        new WebhookSecurityValidator(objectMapper, host -> List.of(address("127.0.0.1")));

    assertThrows(
        AppException.class,
        () ->
            validator.validateLive(
                connector("http://localhost:8080"),
                policy(true, List.of("localhost"), List.of("POST")),
                "POST",
                URI.create("http://localhost:8080/hook"),
                "{}"));
  }

  @Test
  void blockMetadataIp() {
    WebhookSecurityValidator validator =
        new WebhookSecurityValidator(objectMapper, host -> List.of(address("169.254.169.254")));

    assertThrows(
        AppException.class,
        () ->
            validator.validateLive(
                connector("http://169.254.169.254"),
                policy(true, List.of("169.254.169.254"), List.of("GET")),
                "GET",
                URI.create("http://169.254.169.254/latest/meta-data"),
                ""));
  }

  @Test
  void blockUnsupportedMethod() {
    WebhookSecurityValidator validator =
        new WebhookSecurityValidator(objectMapper, host -> List.of(address("93.184.216.34")));

    assertThrows(
        AppException.class,
        () ->
            validator.validateLive(
                connector("https://ops.example.com"),
                policy(true, List.of("ops.example.com"), List.of("POST")),
                "DELETE",
                URI.create("https://ops.example.com/internal/restart"),
                "{}"));
  }

  @Test
  void blockWhenAnyResolvedAddressIsPrivate() {
    WebhookSecurityValidator validator =
        new WebhookSecurityValidator(
            objectMapper, host -> List.of(address("93.184.216.34"), address("10.0.0.8")));

    assertThrows(
        AppException.class,
        () ->
            validator.validateLive(
                connector("https://ops.example.com"),
                policy(true, List.of("ops.example.com"), List.of("POST")),
                "POST",
                URI.create("https://ops.example.com/internal/restart"),
                "{}"));
  }

  @Test
  void blockUrlUserInfo() {
    WebhookSecurityValidator validator =
        new WebhookSecurityValidator(objectMapper, host -> List.of(address("93.184.216.34")));

    assertThrows(
        AppException.class,
        () ->
            validator.validateLive(
                connector("https://ops.example.com"),
                policy(true, List.of("ops.example.com"), List.of("POST")),
                "POST",
                URI.create("https://user:pass@ops.example.com/internal/restart"),
                "{}"));
  }

  private InetAddress address(String value) {
    try {
      return InetAddress.getByName(value);
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private WebhookConnectorRecord connector(String baseUrl) {
    return new WebhookConnectorRecord(
        "whc_1",
        "tenant_1",
        "ops",
        "desc",
        baseUrl,
        "POST",
        "{}",
        json.write(List.of("authorization", "x-api-key")),
        true,
        "alice",
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private WebhookPolicyRecord policy(boolean allowLive, List<String> hosts, List<String> methods) {
    return new WebhookPolicyRecord(
        "whp_1",
        "tenant_1",
        "whc_1",
        allowLive,
        json.write(hosts),
        json.write(methods),
        true,
        true,
        true,
        32768,
        5000,
        true,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }
}
