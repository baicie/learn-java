package io.aegisops.integration.zabbix;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class ZabbixWebhookTokenVerifier {
  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final String TOKEN_CONTEXT = "aegisops:zabbix-webhook:";
  private static final String TOKEN_PREFIX = "zwh_";

  private final ZabbixWebhookProperties properties;

  public ZabbixWebhookTokenVerifier(ZabbixWebhookProperties properties) {
    this.properties = properties;
  }

  public boolean verify(String datasourceId, String providedToken) {
    if (datasourceId == null || datasourceId.isBlank()) {
      return false;
    }
    if (providedToken == null || providedToken.isBlank()) {
      return false;
    }

    String signingSecret = properties.token();
    if (signingSecret == null || signingSecret.isBlank()) {
      return false;
    }

    byte[] expected = tokenForDatasource(datasourceId).getBytes(StandardCharsets.UTF_8);
    byte[] provided = providedToken.trim().getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(expected, provided);
  }

  public String tokenForDatasource(String datasourceId) {
    if (datasourceId == null || datasourceId.isBlank()) {
      throw new IllegalArgumentException("datasource id is required");
    }
    String signingSecret = properties.token();
    if (signingSecret == null || signingSecret.isBlank()) {
      throw new IllegalStateException("Zabbix webhook signing secret is not configured");
    }

    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(
          new SecretKeySpec(signingSecret.trim().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
      byte[] signature =
          mac.doFinal((TOKEN_CONTEXT + datasourceId.trim()).getBytes(StandardCharsets.UTF_8));
      return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
    } catch (java.security.InvalidKeyException exception) {
      throw new IllegalStateException("Zabbix webhook signing secret is invalid", exception);
    }
  }
}
