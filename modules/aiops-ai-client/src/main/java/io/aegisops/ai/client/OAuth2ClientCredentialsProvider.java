package io.aegisops.ai.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

public final class OAuth2ClientCredentialsProvider implements AgentCredentialProvider {
  private static final long REFRESH_SKEW_SECONDS = 30;

  private final AgentServiceAuthProperties properties;
  private final RestTemplate restTemplate;
  private final Clock clock;
  private CachedToken cachedToken;

  public OAuth2ClientCredentialsProvider(
      AgentServiceAuthProperties properties, RestTemplate restTemplate, Clock clock) {
    this.properties = properties;
    this.restTemplate = restTemplate;
    this.clock = clock;
  }

  @Override
  public void apply(HttpHeaders headers) {
    headers.setBearerAuth(accessToken());
  }

  private synchronized String accessToken() {
    Instant now = clock.instant();
    if (cachedToken != null && cachedToken.refreshAfter().isAfter(now)) {
      return cachedToken.value();
    }

    validateConfiguration();
    HttpHeaders headers = new HttpHeaders();
    headers.setBasicAuth(properties.getClientId(), properties.getClientSecret());
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    if (properties.getScope() != null && !properties.getScope().isBlank()) {
      form.add("scope", properties.getScope().trim());
    }

    OAuthTokenResponse response =
        restTemplate.postForObject(
            properties.getTokenUri(), new HttpEntity<>(form, headers), OAuthTokenResponse.class);
    if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
      throw new IllegalStateException("OAuth2 token endpoint returned no access_token");
    }

    long expiresIn = response.expiresIn() == null ? 60 : Math.max(1, response.expiresIn());
    long refreshIn = Math.max(1, expiresIn - REFRESH_SKEW_SECONDS);
    cachedToken = new CachedToken(response.accessToken(), now.plusSeconds(refreshIn));
    return cachedToken.value();
  }

  private void validateConfiguration() {
    if (properties.getTokenUri() == null || properties.getTokenUri().isBlank()) {
      throw new IllegalStateException("aiops.agent.auth.token-uri is required");
    }
    if (properties.getClientId() == null || properties.getClientId().isBlank()) {
      throw new IllegalStateException("aiops.agent.auth.client-id is required");
    }
    if (properties.getClientSecret() == null || properties.getClientSecret().isBlank()) {
      throw new IllegalStateException("aiops.agent.auth.client-secret is required");
    }
  }

  private record CachedToken(String value, Instant refreshAfter) {}

  private record OAuthTokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("token_type") String tokenType,
      @JsonProperty("expires_in") Long expiresIn) {}
}
