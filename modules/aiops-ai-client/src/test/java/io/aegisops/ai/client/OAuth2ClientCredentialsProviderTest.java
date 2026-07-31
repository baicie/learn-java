package io.aegisops.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
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

  @Test
  void reusesUnexpiredTokenAndSuppressesRetryStormWhenRefreshFails() {
    AgentServiceAuthProperties properties = validProperties();
    MutableClock clock = new MutableClock(Instant.parse("2026-07-30T08:00:00Z"));
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(requestTo(properties.getTokenUri()))
        .andRespond(
            withSuccess(
                """
                {
                  "access_token": "still-valid-token",
                  "token_type": "Bearer",
                  "expires_in": 60
                }
                """,
                MediaType.APPLICATION_JSON));
    server.expect(requestTo(properties.getTokenUri())).andRespond(withServerError());
    OAuth2ClientCredentialsProvider provider =
        new OAuth2ClientCredentialsProvider(properties, restTemplate, clock);

    HttpHeaders initial = new HttpHeaders();
    provider.apply(initial);
    clock.advance(Duration.ofSeconds(31));
    HttpHeaders fallback = new HttpHeaders();
    HttpHeaders suppressedRetry = new HttpHeaders();
    assertThatCode(() -> provider.apply(fallback)).doesNotThrowAnyException();
    provider.apply(suppressedRetry);

    assertThat(fallback.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer still-valid-token");
    assertThat(suppressedRetry.getFirst(HttpHeaders.AUTHORIZATION))
        .isEqualTo("Bearer still-valid-token");
    server.verify();
  }

  @Test
  void neverFallsBackToHardExpiredToken() {
    AgentServiceAuthProperties properties = validProperties();
    MutableClock clock = new MutableClock(Instant.parse("2026-07-30T08:00:00Z"));
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(requestTo(properties.getTokenUri()))
        .andRespond(
            withSuccess(
                """
                {
                  "access_token": "short-lived-token",
                  "token_type": "Bearer",
                  "expires_in": 40
                }
                """,
                MediaType.APPLICATION_JSON));
    server.expect(requestTo(properties.getTokenUri())).andRespond(withServerError());
    OAuth2ClientCredentialsProvider provider =
        new OAuth2ClientCredentialsProvider(properties, restTemplate, clock);

    provider.apply(new HttpHeaders());
    clock.advance(Duration.ofSeconds(41));

    assertThatThrownBy(() -> provider.apply(new HttpHeaders()))
        .isInstanceOf(RuntimeException.class);
    server.verify();
  }

  @Test
  void neverFallsBackToTokenThatExpiresDuringFailedRefresh() {
    AgentServiceAuthProperties properties = validProperties();
    MutableClock clock = new MutableClock(Instant.parse("2026-07-30T08:00:00Z"));
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(requestTo(properties.getTokenUri()))
        .andRespond(
            withSuccess(
                """
                {
                  "access_token": "expiring-token",
                  "token_type": "Bearer",
                  "expires_in": 40
                }
                """,
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(properties.getTokenUri()))
        .andRespond(
            request -> {
              clock.advance(Duration.ofSeconds(10));
              return withServerError().createResponse(request);
            });
    OAuth2ClientCredentialsProvider provider =
        new OAuth2ClientCredentialsProvider(properties, restTemplate, clock);

    provider.apply(new HttpHeaders());
    clock.advance(Duration.ofSeconds(31));

    assertThatThrownBy(() -> provider.apply(new HttpHeaders()))
        .isInstanceOf(RuntimeException.class);
    server.verify();
  }

  @Test
  void startsRefreshFailureBackoffWhenSlowFailureCompletes() {
    AgentServiceAuthProperties properties = validProperties();
    MutableClock clock = new MutableClock(Instant.parse("2026-07-30T08:00:00Z"));
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(requestTo(properties.getTokenUri()))
        .andRespond(
            withSuccess(
                """
                {
                  "access_token": "still-valid-token",
                  "token_type": "Bearer",
                  "expires_in": 60
                }
                """,
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(properties.getTokenUri()))
        .andRespond(
            request -> {
              clock.advance(Duration.ofSeconds(10));
              return withServerError().createResponse(request);
            });
    OAuth2ClientCredentialsProvider provider =
        new OAuth2ClientCredentialsProvider(properties, restTemplate, clock);

    provider.apply(new HttpHeaders());
    clock.advance(Duration.ofSeconds(31));
    HttpHeaders fallback = new HttpHeaders();
    HttpHeaders suppressedRetry = new HttpHeaders();
    provider.apply(fallback);
    provider.apply(suppressedRetry);

    assertThat(fallback.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer still-valid-token");
    assertThat(suppressedRetry.getFirst(HttpHeaders.AUTHORIZATION))
        .isEqualTo("Bearer still-valid-token");
    server.verify();
  }

  @Test
  void rejectsOAuthTokenLifetimeOverFiveMinutes() {
    AgentServiceAuthProperties properties = validProperties();
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(requestTo(properties.getTokenUri()))
        .andRespond(
            withSuccess(
                """
                {
                  "access_token": "long-lived-token",
                  "token_type": "Bearer",
                  "expires_in": 301
                }
                """,
                MediaType.APPLICATION_JSON));
    OAuth2ClientCredentialsProvider provider =
        new OAuth2ClientCredentialsProvider(properties, restTemplate, Clock.systemUTC());

    assertThatThrownBy(() -> provider.apply(new HttpHeaders()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("short lived");
    server.verify();
  }

  private static AgentServiceAuthProperties validProperties() {
    AgentServiceAuthProperties properties = new AgentServiceAuthProperties();
    properties.setTokenUri("https://idp.example.com/token");
    properties.setClientId("aiops-server");
    properties.setClientSecret("client-secret");
    return properties;
  }

  private static final class MutableClock extends Clock {
    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
