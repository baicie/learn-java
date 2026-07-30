package io.aegisops.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class OAuth2ClientCredentialsProviderTest {
  @Test
  void obtainsAndCachesClientCredentialsToken() {
    AgentServiceAuthProperties properties = new AgentServiceAuthProperties();
    properties.setTokenUri("https://idp.example.com/realms/aegisops/token");
    properties.setClientId("aiops-server");
    properties.setClientSecret("client-secret");
    properties.setScope("agent:diagnose agent:resume");

    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(requestTo(properties.getTokenUri()))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic YWlvcHMtc2VydmVyOmNsaWVudC1zZWNyZXQ="))
        .andExpect(content().string(containsString("grant_type=client_credentials")))
        .andExpect(content().string(containsString("scope=agent%3Adiagnose+agent%3Aresume")))
        .andRespond(
            withSuccess(
                """
                {
                  "access_token": "short-lived-service-token",
                  "token_type": "Bearer",
                  "expires_in": 300
                }
                """,
                MediaType.APPLICATION_JSON));

    OAuth2ClientCredentialsProvider provider =
        new OAuth2ClientCredentialsProvider(
            properties,
            restTemplate,
            Clock.fixed(Instant.parse("2026-07-30T08:00:00Z"), ZoneOffset.UTC));

    HttpHeaders first = new HttpHeaders();
    HttpHeaders second = new HttpHeaders();
    provider.apply(first);
    provider.apply(second);

    assertThat(first.getFirst(HttpHeaders.AUTHORIZATION))
        .isEqualTo("Bearer short-lived-service-token");
    assertThat(second.getFirst(HttpHeaders.AUTHORIZATION))
        .isEqualTo("Bearer short-lived-service-token");
    server.verify();
  }

  @Test
  void rejectsIncompleteOAuthConfiguration() {
    AgentServiceAuthProperties properties = new AgentServiceAuthProperties();
    properties.setTokenUri("https://idp.example.com/token");

    OAuth2ClientCredentialsProvider provider =
        new OAuth2ClientCredentialsProvider(properties, new RestTemplate(), Clock.systemUTC());

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> provider.apply(new HttpHeaders()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("client-id");
  }
}
