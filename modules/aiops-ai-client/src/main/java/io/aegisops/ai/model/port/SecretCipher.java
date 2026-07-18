package io.aegisops.ai.model.port;

public interface SecretCipher {
  String encrypt(String plaintext);

  String decrypt(String ciphertext);
}
