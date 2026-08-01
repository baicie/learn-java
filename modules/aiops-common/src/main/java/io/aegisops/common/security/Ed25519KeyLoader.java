package io.aegisops.common.security;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class Ed25519KeyLoader {
  private Ed25519KeyLoader() {}

  public static PrivateKey loadPrivateKey(String pem) {
    try {
      byte[] encoded = decodePem(pem, "PRIVATE KEY");
      return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(encoded));
    } catch (InvalidDiagnosisGrantException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new InvalidDiagnosisGrantException("Invalid Ed25519 private key", exception);
    }
  }

  public static PublicKey loadPublicKey(String pem) {
    try {
      byte[] encoded = decodePem(pem, "PUBLIC KEY");
      return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
    } catch (InvalidDiagnosisGrantException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new InvalidDiagnosisGrantException("Invalid Ed25519 public key", exception);
    }
  }

  private static byte[] decodePem(String pem, String type) {
    if (pem == null || pem.isBlank()) {
      throw new InvalidDiagnosisGrantException("Ed25519 " + type.toLowerCase() + " is required");
    }
    String begin = "-----BEGIN " + type + "-----";
    String end = "-----END " + type + "-----";
    int beginIndex = pem.indexOf(begin);
    int endIndex = pem.indexOf(end);
    if (beginIndex < 0 || endIndex <= beginIndex) {
      throw new InvalidDiagnosisGrantException("Invalid Ed25519 " + type.toLowerCase() + " PEM");
    }
    String body = pem.substring(beginIndex + begin.length(), endIndex).replaceAll("\\s", "");
    try {
      return Base64.getDecoder().decode(body);
    } catch (IllegalArgumentException exception) {
      throw new InvalidDiagnosisGrantException(
          "Invalid Ed25519 " + type.toLowerCase() + " PEM", exception);
    }
  }
}
