package io.aegisops.security;

import io.aegisops.common.security.Ed25519KeyLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.LinkedHashMap;
import java.util.Map;

public record DiagnosisGrantVerificationKeys(Map<String, PublicKey> keys) {
  public DiagnosisGrantVerificationKeys {
    keys = keys == null ? Map.of() : Map.copyOf(keys);
    if (keys.isEmpty()) {
      throw new IllegalStateException("At least one diagnosis grant public key is required");
    }
  }

  static DiagnosisGrantVerificationKeys load(AiopsSecurityProperties properties) {
    properties.validateDiagnosisGrant();
    requireText(
        properties.getDiagnosisGrantKeyId(), "aiops.security.diagnosis-grant-key-id is required");
    requireText(
        properties.getDiagnosisGrantPublicKeyFile(),
        "aiops.security.diagnosis-grant-public-key-file is required");
    Map<String, PublicKey> keys = new LinkedHashMap<>();
    keys.put(
        properties.getDiagnosisGrantKeyId(),
        loadPublicKey(properties.getDiagnosisGrantPublicKeyFile()));

    boolean hasPreviousId = !isBlank(properties.getDiagnosisGrantPreviousKeyId());
    boolean hasPreviousFile = !isBlank(properties.getDiagnosisGrantPreviousPublicKeyFile());
    if (hasPreviousId != hasPreviousFile) {
      throw new IllegalStateException(
          "Previous diagnosis grant key id and public key file must be configured together");
    }
    if (hasPreviousId) {
      if (keys.containsKey(properties.getDiagnosisGrantPreviousKeyId())) {
        throw new IllegalStateException("Diagnosis grant current and previous key ids must differ");
      }
      keys.put(
          properties.getDiagnosisGrantPreviousKeyId(),
          loadPublicKey(properties.getDiagnosisGrantPreviousPublicKeyFile()));
    }
    return new DiagnosisGrantVerificationKeys(keys);
  }

  private static PublicKey loadPublicKey(String file) {
    try {
      return Ed25519KeyLoader.loadPublicKey(Files.readString(Path.of(file)));
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to read diagnosis grant public key", exception);
    }
  }

  private static void requireText(String value, String message) {
    if (isBlank(value)) {
      throw new IllegalStateException(message);
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
