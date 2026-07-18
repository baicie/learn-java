package io.aegisops.ai.model.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AesGcmSecretCipherTest {
  @Test
  void encryptsWithRandomNonceAndCanDecrypt() {
    var cipher = new AesGcmSecretCipher("unit-test-secret-with-enough-entropy");

    String first = cipher.encrypt("sk-sensitive");
    String second = cipher.encrypt("sk-sensitive");

    assertThat(first).doesNotContain("sk-sensitive").isNotEqualTo(second);
    assertThat(cipher.decrypt(first)).isEqualTo("sk-sensitive");
  }

  @Test
  void refusesBlankMasterSecret() {
    assertThatThrownBy(() -> new AesGcmSecretCipher(" "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
