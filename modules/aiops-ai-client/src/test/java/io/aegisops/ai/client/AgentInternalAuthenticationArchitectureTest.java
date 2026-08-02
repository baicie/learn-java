package io.aegisops.ai.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AgentInternalAuthenticationArchitectureTest {
  @Test
  void doesNotShipLegacyOauthCredentialTypes() {
    assertMissing("io.aegisops.ai.client.OAuth2ClientCredentialsProvider");
    assertMissing("io.aegisops.ai.client.AgentServiceAuthProperties");
    assertMissing("io.aegisops.ai.client.AgentCredentialProvider");
    assertMissing("io.aegisops.ai.client.AgentServiceAuthConfiguration");
  }

  private void assertMissing(String className) {
    assertThatThrownBy(() -> Class.forName(className, true, getClass().getClassLoader()))
        .isInstanceOf(ClassNotFoundException.class);
  }
}
