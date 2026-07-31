package io.aegisops.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

class AgentServiceAuthConfigurationTest {
  @Test
  void alwaysUsesOAuth2ClientCredentials() {
    AgentServiceAuthProperties authProperties = validAuthProperties();

    AgentCredentialProvider provider =
        new AgentServiceAuthConfiguration().agentCredentialProvider(authProperties);

    assertThat(provider).isInstanceOf(OAuth2ClientCredentialsProvider.class);
  }

  @Test
  void rejectsMissingTokenUriAtStartup() {
    AgentServiceAuthProperties authProperties = validAuthProperties();
    authProperties.setTokenUri("");

    assertThatThrownBy(
            () -> new AgentServiceAuthConfiguration().agentCredentialProvider(authProperties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("token-uri");
  }

  @Test
  void rejectsMissingClientIdAtStartup() {
    AgentServiceAuthProperties authProperties = validAuthProperties();
    authProperties.setClientId("");

    assertThatThrownBy(
            () -> new AgentServiceAuthConfiguration().agentCredentialProvider(authProperties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("client-id");
  }

  @Test
  void rejectsMissingClientSecretAtStartup() {
    AgentServiceAuthProperties authProperties = validAuthProperties();
    authProperties.setClientSecret("");

    assertThatThrownBy(
            () -> new AgentServiceAuthConfiguration().agentCredentialProvider(authProperties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("client-secret");
  }

  @Test
  void configuresBoundedOAuthTokenEndpointTimeouts() throws Exception {
    AgentCredentialProvider provider =
        new AgentServiceAuthConfiguration().agentCredentialProvider(validAuthProperties());

    Field restTemplateField =
        OAuth2ClientCredentialsProvider.class.getDeclaredField("restTemplate");
    restTemplateField.setAccessible(true);
    RestTemplate restTemplate = (RestTemplate) restTemplateField.get(provider);
    assertThat(restTemplate.getRequestFactory()).isInstanceOf(SimpleClientHttpRequestFactory.class);
    SimpleClientHttpRequestFactory requestFactory =
        (SimpleClientHttpRequestFactory) restTemplate.getRequestFactory();

    assertThat(intField(requestFactory, "connectTimeout")).isEqualTo(3000);
    assertThat(intField(requestFactory, "readTimeout")).isEqualTo(5000);
  }

  private static int intField(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.getInt(target);
  }

  private static AgentServiceAuthProperties validAuthProperties() {
    AgentServiceAuthProperties authProperties = new AgentServiceAuthProperties();
    authProperties.setTokenUri("https://idp.example.com/oauth2/token");
    authProperties.setClientId("aiops-server");
    authProperties.setClientSecret("client-secret");
    return authProperties;
  }
}
