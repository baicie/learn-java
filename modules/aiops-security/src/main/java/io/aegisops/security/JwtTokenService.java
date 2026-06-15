package io.aegisops.security;

import io.aegisops.common.exception.AppException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
  private final JwtProperties properties;

  public JwtTokenService(JwtProperties properties) {
    this.properties = properties;
  }

  public String issue(UserPrincipal principal) {
    long exp = Instant.now().getEpochSecond() + properties.getTtlSeconds();
    String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
    String payload =
        b64(
            String.format(
                "{\"sub\":\"%s\",\"tid\":\"%s\",\"name\":\"%s\",\"exp\":%d}",
                escape(principal.id()),
                escape(principal.tenantId()),
                escape(principal.username()),
                exp));
    String signature = sign(header + "." + payload);
    return header + "." + payload + "." + signature;
  }

  public JwtClaims verify(String token) {
    String[] parts = token.split("\\.");
    if (parts.length != 3) {
      throw new AppException("INVALID_TOKEN", "Invalid token");
    }
    String expected = sign(parts[0] + "." + parts[1]);
    if (!constantTimeEquals(expected, parts[2])) {
      throw new AppException("INVALID_TOKEN", "Invalid token signature");
    }
    String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    String sub = findString(payload, "sub");
    String tenantId = findString(payload, "tid");
    long exp = findLong(payload, "exp");
    if (Instant.now().getEpochSecond() > exp) {
      throw new AppException("TOKEN_EXPIRED", "Token expired");
    }
    return new JwtClaims(sub, tenantId);
  }

  private String sign(String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new AppException("JWT_ERROR", e.getMessage());
    }
  }

  private static String b64(String json) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static String findString(String json, String key) {
    String marker = "\"" + key + "\":\"";
    int start = json.indexOf(marker);
    if (start < 0) throw new AppException("INVALID_TOKEN", "Missing claim: " + key);
    start += marker.length();
    int end = json.indexOf('"', start);
    if (end < 0) throw new AppException("INVALID_TOKEN", "Invalid claim: " + key);
    return json.substring(start, end);
  }

  private static long findLong(String json, String key) {
    String marker = "\"" + key + "\":";
    int start = json.indexOf(marker);
    if (start < 0) throw new AppException("INVALID_TOKEN", "Missing claim: " + key);
    start += marker.length();
    int end = start;
    while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
    return Long.parseLong(json.substring(start, end));
  }

  private static boolean constantTimeEquals(String a, String b) {
    if (a.length() != b.length()) return false;
    int result = 0;
    for (int i = 0; i < a.length(); i++) {
      result |= a.charAt(i) ^ b.charAt(i);
    }
    return result == 0;
  }

  public record JwtClaims(String userId, String tenantId) {}
}
