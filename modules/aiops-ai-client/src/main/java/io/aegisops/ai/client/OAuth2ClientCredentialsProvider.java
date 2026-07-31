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
  private static final long REFRESH_FAILURE_RETRY_SECONDS = 5;
  private static final long MAX_TOKEN_LIFETIME_SECONDS = 300;

  private final AgentServiceAuthProperties properties;
  private final RestTemplate restTemplate;
  private final Clock clock;
  private final Object refreshLock = new Object();
  private volatile CachedToken cachedToken;
  private Instant retryAfter = Instant.EPOCH;

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

  private String accessToken() {
    Instant now = clock.instant();
    CachedToken snapshot = cachedToken;
    if (isFresh(snapshot, now)) {
      return snapshot.value();
    }

    synchronized (refreshLock) {
      now = clock.instant();
      snapshot = cachedToken;
      if (isFresh(snapshot, now)) {
        return snapshot.value();
      }
      if (retryAfter.isAfter(now)) {
        if (isUnexpired(snapshot, now)) {
          return snapshot.value();
        }
        throw new IllegalStateException("OAuth2 token refresh is temporarily unavailable");
      }

      try {
        CachedToken refreshed = refresh(now);
        cachedToken = refreshed;
        retryAfter = Instant.EPOCH;
        return refreshed.value();
      } catch (RuntimeException exception) {
        Instant failureNow = clock.instant();
        retryAfter = failureNow.plusSeconds(REFRESH_FAILURE_RETRY_SECONDS);
        if (isUnexpired(snapshot, failureNow)) {
          return snapshot.value();
        }
        throw exception;
      }
    }
  }

  private CachedToken refresh(Instant now) {
    properties.validate();
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
    if (expiresIn > MAX_TOKEN_LIFETIME_SECONDS) {
      throw new IllegalStateException("OAuth2 access token must be short lived (<= 300 seconds)");
    }
    long refreshIn = Math.max(1, expiresIn - REFRESH_SKEW_SECONDS);
    return new CachedToken(
        response.accessToken(), now.plusSeconds(refreshIn), now.plusSeconds(expiresIn));
  }

  private boolean isFresh(CachedToken token, Instant now) {
    return token != null && token.refreshAfter().isAfter(now);
  }

  private boolean isUnexpired(CachedToken token, Instant now) {
    return token != null && token.expiresAt().isAfter(now);
  }

  private record CachedToken(String value, Instant refreshAfter, Instant expiresAt) {}

  private record OAuthTokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("token_type") String tokenType,
      @JsonProperty("expires_in") Long expiresIn) {}
}
