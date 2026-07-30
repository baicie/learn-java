package io.aegisops.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class DiagnosisGrantCodec {
  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final byte[] JWT_HEADER =
      "{\"alg\":\"HS256\",\"typ\":\"JWT\",\"kid\":\"diagnosis-grant-v1\"}"
          .getBytes(StandardCharsets.UTF_8);

  private final ObjectMapper objectMapper;
  private final Clock clock;

  public DiagnosisGrantCodec(ObjectMapper objectMapper, Clock clock) {
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public String issue(String secret, DiagnosisGrantClaims claims) {
    validateSecret(secret);
    validateClaims(claims);

    GrantPayload payload =
        new GrantPayload(
            claims.issuer(),
            claims.audience(),
            claims.tenantId(),
            claims.incidentId(),
            claims.traceId(),
            claims.issuedAt().getEpochSecond(),
            claims.expiresAt().getEpochSecond(),
            UUID.randomUUID().toString());

    try {
      String header = encode(JWT_HEADER);
      String body = encode(objectMapper.writeValueAsBytes(payload));
      String signingInput = header + "." + body;
      return signingInput + "." + encode(sign(secret, signingInput));
    } catch (InvalidDiagnosisGrantException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new InvalidDiagnosisGrantException("Unable to issue diagnosis grant", exception);
    }
  }

  public DiagnosisGrantClaims verify(String secret, String token, String expectedAudience) {
    validateSecret(secret);
    if (token == null || token.isBlank()) {
      throw new InvalidDiagnosisGrantException("Diagnosis grant is required");
    }

    String[] parts = token.split("\\.", -1);
    if (parts.length != 3) {
      throw new InvalidDiagnosisGrantException("Diagnosis grant must have three JWT segments");
    }

    try {
      String signingInput = parts[0] + "." + parts[1];
      byte[] expectedSignature = sign(secret, signingInput);
      byte[] actualSignature = Base64.getUrlDecoder().decode(parts[2]);
      if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
        throw new InvalidDiagnosisGrantException("Invalid diagnosis grant signature");
      }

      GrantPayload payload =
          objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), GrantPayload.class);
      Instant now = clock.instant();
      if (!expectedAudience.equals(payload.aud())) {
        throw new InvalidDiagnosisGrantException("Invalid diagnosis grant audience");
      }
      if (payload.exp() <= now.getEpochSecond()) {
        throw new InvalidDiagnosisGrantException("Diagnosis grant expired");
      }
      if (payload.iat() > now.plusSeconds(30).getEpochSecond()) {
        throw new InvalidDiagnosisGrantException("Diagnosis grant issued in the future");
      }

      DiagnosisGrantClaims claims =
          new DiagnosisGrantClaims(
              payload.iss(),
              payload.aud(),
              payload.tenantId(),
              payload.incidentId(),
              payload.traceId(),
              Instant.ofEpochSecond(payload.iat()),
              Instant.ofEpochSecond(payload.exp()));
      validateClaims(claims);
      return claims;
    } catch (InvalidDiagnosisGrantException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new InvalidDiagnosisGrantException("Invalid diagnosis grant", exception);
    }
  }

  private byte[] sign(String secret, String signingInput) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
      return mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
    } catch (Exception exception) {
      throw new InvalidDiagnosisGrantException("Unable to sign diagnosis grant", exception);
    }
  }

  private static String encode(byte[] value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }

  private static void validateSecret(String secret) {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new InvalidDiagnosisGrantException(
          "Diagnosis grant secret must contain at least 32 bytes");
    }
  }

  private static void validateClaims(DiagnosisGrantClaims claims) {
    if (claims == null
        || isBlank(claims.issuer())
        || isBlank(claims.audience())
        || isBlank(claims.tenantId())
        || isBlank(claims.incidentId())
        || isBlank(claims.traceId())
        || claims.issuedAt() == null
        || claims.expiresAt() == null
        || !claims.expiresAt().isAfter(claims.issuedAt())) {
      throw new InvalidDiagnosisGrantException("Diagnosis grant claims are incomplete");
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private record GrantPayload(
      String iss,
      String aud,
      String tenantId,
      String incidentId,
      String traceId,
      long iat,
      long exp,
      String jti) {}
}
