package io.aegisops.runner.executor.ansible;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AnsibleOutputMaskerTest {
  @Test
  void maskPlainSensitiveValues() {
    AnsibleOutputMasker masker = new AnsibleOutputMasker();

    String masked =
        masker.mask("password=123456 token:abcdef secret = qwerty api_key=xxx normal=value");

    assertFalse(masked.contains("123456"));
    assertFalse(masked.contains("abcdef"));
    assertFalse(masked.contains("qwerty"));
    assertFalse(masked.contains("api_key=xxx"));
    assertTrue(masked.contains("normal=value"));
  }

  @Test
  void maskJsonSensitiveValues() {
    AnsibleOutputMasker masker = new AnsibleOutputMasker();

    String masked =
        masker.mask(
            "{\"password\":\"123456\",\"api_key\":\"abcdef\",\"token\":\"t-123\",\"normal\":\"ok\"}");

    assertFalse(masked.contains("123456"));
    assertFalse(masked.contains("abcdef"));
    assertFalse(masked.contains("t-123"));
    assertTrue(masked.contains("\"normal\":\"ok\""));
    assertTrue(masked.contains("\"password\":\"***\""));
    assertTrue(masked.contains("\"api_key\":\"***\""));
  }

  @Test
  void maskBearerAndBasicTokens() {
    AnsibleOutputMasker masker = new AnsibleOutputMasker();

    String masked =
        masker.mask(
            "Authorization: Bearer abcdef.123456\nProxy-Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==");

    assertFalse(masked.contains("abcdef.123456"));
    assertFalse(masked.contains("QWxhZGRpbjpvcGVuIHNlc2FtZQ"));
    assertTrue(masked.contains("Bearer ***"));
    assertTrue(masked.contains("Basic ***"));
  }
}
