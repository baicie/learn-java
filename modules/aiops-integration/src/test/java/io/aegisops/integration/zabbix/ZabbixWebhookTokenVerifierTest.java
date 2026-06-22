package io.aegisops.integration.zabbix;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ZabbixWebhookTokenVerifierTest {

  @Test
  void shouldVerifyToken() {
    ZabbixWebhookTokenVerifier verifier =
        new ZabbixWebhookTokenVerifier(new ZabbixWebhookProperties("secret"));

    assertThat(verifier.verify("secret")).isTrue();
  }

  @Test
  void shouldRejectInvalidToken() {
    ZabbixWebhookTokenVerifier verifier =
        new ZabbixWebhookTokenVerifier(new ZabbixWebhookProperties("secret"));

    assertThat(verifier.verify("bad")).isFalse();
    assertThat(verifier.verify(null)).isFalse();
    assertThat(verifier.verify(" ")).isFalse();
  }

  @Test
  void shouldRejectWhenExpectedTokenMissing() {
    assertThat(new ZabbixWebhookTokenVerifier(new ZabbixWebhookProperties(null)).verify("secret"))
        .isFalse();

    assertThat(new ZabbixWebhookTokenVerifier(new ZabbixWebhookProperties(" ")).verify("secret"))
        .isFalse();
  }
}
