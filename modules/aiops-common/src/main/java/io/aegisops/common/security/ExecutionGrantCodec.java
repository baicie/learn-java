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

public final class ExecutionGrantCodec {
  private static final String SIGNATURE_ALGORITHM = "Ed25519";
  private static final int MIN_DURATION_SECONDS = 30;
  private static final int MAX_DURATION_SECONDS = 86_400;
  private static final int MAX_GRANT_LIFETIME_SECONDS = 172_800;
  private static final Duration MAX_CLOCK_SKEW = Duration.ofSeconds(30);

  private final ObjectMapper objectMapper;
  private final Clock clock;

  public ExecutionGrantCodec(ObjectMapper objectMapper, Clock clock) {
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public String issue(PrivateKey privateKey, String keyId, ExecutionGrantClaims claims) {
    validateSigningKey(privateKey);
    if (isBlank(keyId)) {
      throw new InvalidExecutionGrantException("Execution grant key id is required");
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
            claims.executionId(),
            claims.planId(),
            claims.mode(),
            claims.executionKind(),
            claims.rollbackPlanId(),
            claims.rollbackOfExecutionId(),
            claims.snapshotSha256(),
            claims.maxDurationSeconds(),
            claims.issuedAt().getEpochSecond(),
            claims.expiresAt().getEpochSecond(),
            claims.jti());

    try {
      String header = encode(objectMapper.writeValueAsBytes(new JwtHeader("EdDSA", "JWT", keyId)));
      String body = encode(objectMapper.writeValueAsBytes(payload));
      String signingInput = header + "." + body;
      return signingInput + "." + encode(sign(privateKey, signingInput));
    } catch (InvalidExecutionGrantException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new InvalidExecutionGrantException("Unable to issue execution grant", exception);
    }
  }

  public ExecutionGrantClaims verify(
      Map<String, PublicKey> verificationKeys, String token, String expectedAudience) {
    if (verificationKeys == null || verificationKeys.isEmpty()) {
      throw new InvalidExecutionGrantException("Execution grant verification key is required");
    }
    if (isBlank(token)) {
      throw new InvalidExecutionGrantException("Execution grant is required");
    }
    if (isBlank(expectedAudience)) {
      throw new InvalidExecutionGrantException("Expected execution grant audience is required");
    }

    String[] parts = token.split("\\.", -1);
    if (parts.length != 3) {
      throw new InvalidExecutionGrantException("Execution grant must have three JWT segments");
    }

    try {
      JwtHeader header =
          objectMapper.readValue(Base64.getUrlDecoder().decode(parts[0]), JwtHeader.class);
      if (!"EdDSA".equals(header.alg()) || !"JWT".equals(header.typ())) {
        throw new InvalidExecutionGrantException("Invalid execution grant algorithm");
      }
      PublicKey publicKey = verificationKeys.get(header.kid());
      if (publicKey == null) {
        throw new InvalidExecutionGrantException("Unknown execution grant key id");
      }

      String signingInput = parts[0] + "." + parts[1];
      if (!verify(publicKey, signingInput, Base64.getUrlDecoder().decode(parts[2]))) {
        throw new InvalidExecutionGrantException("Invalid execution grant signature");
      }

      GrantPayload payload =
          objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), GrantPayload.class);
      Instant now = clock.instant();
      if (payload.aud() == null || !payload.aud().contains(expectedAudience)) {
        throw new InvalidExecutionGrantException("Invalid execution grant audience");
      }
      if (payload.exp() <= now.getEpochSecond()) {
        throw new InvalidExecutionGrantException("Execution grant expired");
      }
      if (payload.iat() > now.plus(MAX_CLOCK_SKEW).getEpochSecond()) {
        throw new InvalidExecutionGrantException("Execution grant issued in the future");
      }

      ExecutionGrantClaims claims =
          new ExecutionGrantClaims(
              payload.iss(),
              payload.sub(),
              payload.aud(),
              payload.scope(),
              payload.tenantId(),
              payload.incidentId(),
              payload.executionId(),
              payload.planId(),
              payload.mode(),
              payload.executionKind(),
              payload.rollbackPlanId(),
              payload.rollbackOfExecutionId(),
              payload.snapshotSha256(),
              payload.maxDurationSeconds(),
              Instant.ofEpochSecond(payload.iat()),
              Instant.ofEpochSecond(payload.exp()),
              payload.jti());
      validateClaims(claims);
      return claims;
    } catch (InvalidExecutionGrantException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new InvalidExecutionGrantException("Invalid execution grant", exception);
    }
  }

  private byte[] sign(PrivateKey privateKey, String signingInput) {
    try {
      Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
      signature.initSign(privateKey);
      signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
      return signature.sign();
    } catch (Exception exception) {
      throw new InvalidExecutionGrantException("Unable to sign execution grant", exception);
    }
  }

  private boolean verify(PublicKey publicKey, String signingInput, byte[] actualSignature) {
    try {
      Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
      signature.initVerify(publicKey);
      signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
      return signature.verify(actualSignature);
    } catch (Exception exception) {
      throw new InvalidExecutionGrantException("Unable to verify execution grant", exception);
    }
  }

  private static void validateSigningKey(PrivateKey privateKey) {
    if (privateKey == null
        || !(SIGNATURE_ALGORITHM.equalsIgnoreCase(privateKey.getAlgorithm())
            || "EdDSA".equalsIgnoreCase(privateKey.getAlgorithm()))) {
      throw new InvalidExecutionGrantException("Ed25519 execution grant private key is required");
    }
  }

  private static void validateClaims(ExecutionGrantClaims claims) {
    if (claims == null
        || isBlank(claims.issuer())
        || isBlank(claims.subject())
        || claims.audiences().isEmpty()
        || claims.audiences().stream().anyMatch(ExecutionGrantCodec::isBlank)
        || claims.scopes().isEmpty()
        || claims.scopes().stream().anyMatch(ExecutionGrantCodec::isBlank)
        || isBlank(claims.tenantId())
        || isBlank(claims.incidentId())
        || isBlank(claims.executionId())
        || !claims.subject().equals("execution:" + claims.executionId())
        || isBlank(claims.planId())
        || !("dry_run".equals(claims.mode()) || "live".equals(claims.mode()))
        || !("normal".equals(claims.executionKind()) || "rollback".equals(claims.executionKind()))
        || !validRollbackBinding(claims)
        || isBlank(claims.snapshotSha256())
        || !claims.snapshotSha256().matches("[0-9a-f]{64}")
        || claims.maxDurationSeconds() < MIN_DURATION_SECONDS
        || claims.maxDurationSeconds() > MAX_DURATION_SECONDS
        || claims.issuedAt() == null
        || claims.expiresAt() == null
        || isBlank(claims.jti())
        || !claims.expiresAt().isAfter(claims.issuedAt())
        || Duration.between(claims.issuedAt(), claims.expiresAt()).getSeconds()
            < claims.maxDurationSeconds()
        || Duration.between(claims.issuedAt(), claims.expiresAt()).getSeconds()
            > MAX_GRANT_LIFETIME_SECONDS) {
      throw new InvalidExecutionGrantException("Execution grant claims are incomplete");
    }
  }

  private static boolean validRollbackBinding(ExecutionGrantClaims claims) {
    if ("rollback".equals(claims.executionKind())) {
      return !isBlank(claims.rollbackPlanId()) && !isBlank(claims.rollbackOfExecutionId());
    }
    return isBlank(claims.rollbackPlanId()) && isBlank(claims.rollbackOfExecutionId());
  }

  private static String encode(byte[] value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
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
      String executionId,
      String planId,
      String mode,
      String executionKind,
      String rollbackPlanId,
      String rollbackOfExecutionId,
      String snapshotSha256,
      int maxDurationSeconds,
      long iat,
      long exp,
      String jti) {}
}
