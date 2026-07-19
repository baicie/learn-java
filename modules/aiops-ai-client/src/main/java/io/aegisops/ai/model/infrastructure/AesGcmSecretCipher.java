package io.aegisops.ai.model.infrastructure;

import io.aegisops.ai.model.port.SecretCipher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AesGcmSecretCipher implements SecretCipher {
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;
  private final SecretKeySpec key;
  private final SecureRandom secureRandom = new SecureRandom();

  public AesGcmSecretCipher(@Value("${aiops.ai.model-secret:}") String masterSecret) {
    if (masterSecret == null || masterSecret.isBlank()) {
      throw new IllegalArgumentException("aiops.ai.model-secret is required");
    }
    this.key = new SecretKeySpec(sha256(masterSecret), "AES");
  }

  @Override
  public String encrypt(String plaintext) {
    if (plaintext == null || plaintext.isBlank()) {
      throw new IllegalArgumentException("API Key 不能为空");
    }
    try {
      byte[] nonce = new byte[NONCE_BYTES];
      secureRandom.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
      byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      return "v1."
          + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce)
          + "."
          + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
    } catch (Exception exception) {
      throw new IllegalStateException("AI 模型密钥加密失败", exception);
    }
  }

  @Override
  public String decrypt(String ciphertext) {
    try {
      String[] parts = ciphertext.split("\\.", 3);
      if (parts.length != 3 || !"v1".equals(parts[0])) {
        throw new IllegalArgumentException("unsupported ciphertext format");
      }
      byte[] nonce = Base64.getUrlDecoder().decode(parts[1]);
      byte[] encrypted = Base64.getUrlDecoder().decode(parts[2]);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (Exception exception) {
      throw new IllegalStateException("AI 模型密钥解密失败", exception);
    }
  }

  private byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (Exception exception) {
      throw new IllegalStateException("无法初始化密钥", exception);
    }
  }
}
