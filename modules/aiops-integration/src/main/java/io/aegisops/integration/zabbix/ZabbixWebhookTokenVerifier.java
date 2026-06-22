package io.aegisops.integration.zabbix;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

@Component
public class ZabbixWebhookTokenVerifier {
  private final ZabbixWebhookProperties properties;

  public ZabbixWebhookTokenVerifier(ZabbixWebhookProperties properties) {
    this.properties = properties;
  }

  public boolean verify(String providedToken) {
    String expectedToken = properties.token();
    if (expectedToken == null || expectedToken.isBlank()) {
      return false;
    }
    if (providedToken == null || providedToken.isBlank()) {
      return false;
    }

    byte[] expected = expectedToken.trim().getBytes(StandardCharsets.UTF_8);
    byte[] provided = providedToken.trim().getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(expected, provided);
  }
}
