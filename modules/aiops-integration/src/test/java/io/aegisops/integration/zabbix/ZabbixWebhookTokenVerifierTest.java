package io.aegisops.integration.zabbix;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ZabbixWebhookTokenVerifierTest {

  @Test
  void shouldDeriveAndVerifyDatasourceScopedToken() {
    ZabbixWebhookTokenVerifier verifier =
        new ZabbixWebhookTokenVerifier(new ZabbixWebhookProperties("secret"));
    String token = verifier.tokenForDatasource("ds-a");

    assertThat(verifier.verify("ds-a", token)).isTrue();
    assertThat(verifier.verify("ds-b", token)).isFalse();
    assertThat(verifier.verify("ds-a", "secret")).isFalse();
  }

  @Test
  void shouldRejectInvalidToken() {
    ZabbixWebhookTokenVerifier verifier =
        new ZabbixWebhookTokenVerifier(new ZabbixWebhookProperties("secret"));

    assertThat(verifier.verify("ds-a", "bad")).isFalse();
    assertThat(verifier.verify("ds-a", null)).isFalse();
    assertThat(verifier.verify("ds-a", " ")).isFalse();
    assertThat(verifier.verify(null, "token")).isFalse();
  }

  @Test
  void shouldRejectWhenExpectedTokenMissing() {
    assertThat(
            new ZabbixWebhookTokenVerifier(new ZabbixWebhookProperties(null))
                .verify("ds-a", "secret"))
        .isFalse();

    assertThat(
            new ZabbixWebhookTokenVerifier(new ZabbixWebhookProperties(" "))
                .verify("ds-a", "secret"))
        .isFalse();
  }
}
