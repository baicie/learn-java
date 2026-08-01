package io.aegisops.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DiagnosisGrantCodec {
  private static final String SIGNATURE_ALGORITHM = "Ed25519";
  private static final Duration MAX_GRANT_TTL = Duration.ofMinutes(5);
  private static final Duration MAX_CLOCK_SKEW = Duration.ofSeconds(30);

  private final ObjectMapper objectMapper;
  private final Clock clock;

  public DiagnosisGrantCodec(ObjectMapper objectMapper, Clock clock) {
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public String issue(PrivateKey privateKey, String keyId, DiagnosisGrantClaims claims) {
    validateSigningKey(privateKey);
    if (isBlank(keyId)) {
      throw new InvalidDiagnosisGrantException("Diagnosis grant key id is required");
    }
    validateClaims(claims);

    GrantPayload payload =
        new GrantPayload(
            claims.issuer(),
            claims.subject(),
            claims.audiences(),
            claims.scopes(),
            claims.tenantId(),
            claims.incidentId(),
            claims.diagnosisId(),
            claims.traceId(),
            claims.issuedAt().getEpochSecond(),
            claims.expiresAt().getEpochSecond(),
            claims.jti());

    try {
      String header = encode(objectMapper.writeValueAsBytes(new JwtHeader("EdDSA", "JWT", keyId)));
      String body = encode(objectMapper.writeValueAsBytes(payload));
      String signingInput = header + "." + body;
      return signingInput + "." + encode(sign(privateKey, signingInput));
    } catch (InvalidDiagnosisGrantException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new InvalidDiagnosisGrantException("Unable to issue diagnosis grant", exception);
    }
  }

  public DiagnosisGrantClaims verify(
      Map<String, PublicKey> verificationKeys, String token, String expectedAudience) {
    if (verificationKeys == null || verificationKeys.isEmpty()) {
      throw new InvalidDiagnosisGrantException("Diagnosis grant verification key is required");
    }
    if (token == null || token.isBlank()) {
      throw new InvalidDiagnosisGrantException("Diagnosis grant is required");
    }
    if (isBlank(expectedAudience)) {
      throw new InvalidDiagnosisGrantException("Expected diagnosis grant audience is required");
    }

    String[] parts = token.split("\\.", -1);
    if (parts.length != 3) {
      throw new InvalidDiagnosisGrantException("Diagnosis grant must have three JWT segments");
    }

    try {
      JwtHeader header =
          objectMapper.readValue(Base64.getUrlDecoder().decode(parts[0]), JwtHeader.class);
      if (!"EdDSA".equals(header.alg()) || !"JWT".equals(header.typ())) {
        throw new InvalidDiagnosisGrantException("Invalid diagnosis grant algorithm");
      }
      PublicKey publicKey = verificationKeys.get(header.kid());
      if (publicKey == null) {
        throw new InvalidDiagnosisGrantException("Unknown diagnosis grant key id");
      }

      String signingInput = parts[0] + "." + parts[1];
      byte[] actualSignature = Base64.getUrlDecoder().decode(parts[2]);
      if (!verify(publicKey, signingInput, actualSignature)) {
        throw new InvalidDiagnosisGrantException("Invalid diagnosis grant signature");
      }

      GrantPayload payload =
          objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), GrantPayload.class);
      Instant now = clock.instant();
      if (payload.aud() == null || !payload.aud().contains(expectedAudience)) {
        throw new InvalidDiagnosisGrantException("Invalid diagnosis grant audience");
      }
      if (payload.exp() <= now.getEpochSecond()) {
        throw new InvalidDiagnosisGrantException("Diagnosis grant expired");
      }
      if (payload.iat() > now.plus(MAX_CLOCK_SKEW).getEpochSecond()) {
        throw new InvalidDiagnosisGrantException("Diagnosis grant issued in the future");
      }

      DiagnosisGrantClaims claims =
          new DiagnosisGrantClaims(
              payload.iss(),
              payload.sub(),
              payload.aud(),
              payload.scope(),
              payload.tenantId(),
              payload.incidentId(),
              payload.diagnosisId(),
              payload.traceId(),
              Instant.ofEpochSecond(payload.iat()),
              Instant.ofEpochSecond(payload.exp()),
              payload.jti());
      validateClaims(claims);
      return claims;
    } catch (InvalidDiagnosisGrantException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new InvalidDiagnosisGrantException("Invalid diagnosis grant", exception);
    }
  }

  private byte[] sign(PrivateKey privateKey, String signingInput) {
    try {
      Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
      signature.initSign(privateKey);
      signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
      return signature.sign();
    } catch (Exception exception) {
      throw new InvalidDiagnosisGrantException("Unable to sign diagnosis grant", exception);
    }
  }

  private boolean verify(PublicKey publicKey, String signingInput, byte[] actualSignature) {
    try {
      Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
      signature.initVerify(publicKey);
      signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
      return signature.verify(actualSignature);
    } catch (Exception exception) {
      throw new InvalidDiagnosisGrantException("Unable to verify diagnosis grant", exception);
    }
  }

  private static String encode(byte[] value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }

  private static void validateSigningKey(PrivateKey privateKey) {
    if (privateKey == null
        || !(SIGNATURE_ALGORITHM.equalsIgnoreCase(privateKey.getAlgorithm())
            || "EdDSA".equalsIgnoreCase(privateKey.getAlgorithm()))) {
      throw new InvalidDiagnosisGrantException("Ed25519 diagnosis grant private key is required");
    }
  }

  private static void validateClaims(DiagnosisGrantClaims claims) {
    if (claims == null
        || isBlank(claims.issuer())
        || isBlank(claims.subject())
        || claims.audiences().isEmpty()
        || claims.audiences().stream().anyMatch(DiagnosisGrantCodec::isBlank)
        || claims.scopes().isEmpty()
        || claims.scopes().stream().anyMatch(DiagnosisGrantCodec::isBlank)
        || isBlank(claims.tenantId())
        || isBlank(claims.incidentId())
        || isBlank(claims.diagnosisId())
        || !claims.subject().equals("diagnosis:" + claims.diagnosisId())
        || isBlank(claims.traceId())
        || claims.issuedAt() == null
        || claims.expiresAt() == null
        || isBlank(claims.jti())
        || !claims.expiresAt().isAfter(claims.issuedAt())
        || Duration.between(claims.issuedAt(), claims.expiresAt()).compareTo(MAX_GRANT_TTL) > 0) {
      throw new InvalidDiagnosisGrantException("Diagnosis grant claims are incomplete");
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private record JwtHeader(String alg, String typ, String kid) {}

  private record GrantPayload(
      String iss,
      String sub,
      Set<String> aud,
      List<String> scope,
      String tenantId,
      String incidentId,
      String diagnosisId,
      String traceId,
      long iat,
      long exp,
      String jti) {}
}
